package de.christophlangner.commentator.notification

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.data.account.InstanceStore
import de.christophlangner.commentator.domain.model.Comment
import de.christophlangner.commentator.domain.model.WordPressInstance
import de.christophlangner.commentator.fake.FakeCommentDao
import de.christophlangner.commentator.fake.FakeSettingsRepository
import de.christophlangner.commentator.fake.testComment
import de.christophlangner.commentator.fake.testInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * Prüft die Hintergrundprüfung als Ganzes.
 *
 * Nötig, weil sich diese Kette am Gerät nicht anstossen lässt: WorkManager
 * führt periodische Arbeit nicht vorzeitig aus, auch nicht über
 * `cmd jobscheduler run -f`. Er plant sie dann lediglich neu.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "de-rDE")
class CommentSyncWorkerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var dao: FakeCommentDao
    private lateinit var settings: FakeSettingsRepository
    private lateinit var source: FakeNewCommentSource
    private lateinit var notifier: CommentNotifier
    private lateinit var instanceStore: InstanceStore

    private val instance = testInstance()

    /** Liefert vorgegebene Kommentare und merkt sich, was gemeldet wurde. */
    private class FakeNewCommentSource : NewCommentSource {
        var result: Outcome<List<Comment>> = Outcome.Success(emptyList())
        val notified = mutableListOf<Comment>()
        private var baseline = false

        override suspend fun fetchUnnotified(instance: WordPressInstance) = result

        override suspend fun markNotified(instance: WordPressInstance, comments: List<Comment>) {
            notified += comments
            // Wie die echte Implementierung: Auch eine leere Liste setzt den
            // Ausgangszustand.
            baseline = true
        }

        override suspend fun hasBaseline(instance: WordPressInstance) = baseline
    }

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        dao = FakeCommentDao()
        settings = FakeSettingsRepository()
        source = FakeNewCommentSource()
        val channels = NotificationChannels(context)
        channels.ensureCreated()
        notifier = CommentNotifier(context, channels)

        instanceStore = InstanceStore(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                produceFile = { File(temporaryFolder.root, "settings.preferences_pb") },
            ),
        )
        instanceStore.upsert(instance)
    }

    private fun worker(): CommentSyncWorker =
        TestListenableWorkerBuilder<CommentSyncWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = CommentSyncWorker(
                    appContext,
                    workerParameters,
                    instanceStore,
                    settings,
                    source,
                    notifier,
                )
            })
            .build()

    private val posted get() = shadowOf(
        context.getSystemService(NotificationManager::class.java),
    ).allNotifications

    @Test
    fun `erster Lauf meldet nichts, sondern setzt den Ausgangszustand`() = runTest {
        source.result = Outcome.Success(listOf(testComment(1), testComment(2)))

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        // Sonst käme beim ersten Start der gesamte Rückstand als Schwall.
        assertTrue("Beim ersten Lauf darf nichts gemeldet werden", posted.isEmpty())
        assertEquals(2, source.notified.size)
    }

    @Test
    fun `nach gesetztem Ausgangszustand wird gemeldet`() = runTest {
        source.result = Outcome.Success(listOf(testComment(1)))
        worker().doWork()
        source.notified.clear()

        source.result = Outcome.Success(listOf(testComment(5, author = "Neue Leserin")))
        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, posted.size)
        assertEquals(NotificationChannels.NEW_COMMENTS, posted.single().channelId)
        assertEquals(1, source.notified.size)
    }

    @Test
    fun `erster Lauf ohne offene Kommentare setzt trotzdem den Ausgangszustand`() = runTest {
        source.result = Outcome.Success(emptyList())
        worker().doWork()

        // Ohne diesen Schritt gälte der nächste Lauf erneut als erster und die
        // erste echte Benachrichtigung bliebe aus.
        source.result = Outcome.Success(listOf(testComment(3)))
        worker().doWork()

        assertEquals(1, posted.size)
    }

    @Test
    fun `abgeschaltete Benachrichtigungen unterbinden jede Meldung`() = runTest {
        settings.setNotificationsEnabled(false)
        source.result = Outcome.Success(listOf(testComment(9)))

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(posted.isEmpty())
        assertTrue("Ohne Benachrichtigungen darf auch nichts vermerkt werden", source.notified.isEmpty())
    }

    @Test
    fun `ungueltige Zugangsdaten melden das dauerhaft statt es zu wiederholen`() = runTest {
        source.result = Outcome.Failure(AppError.Unauthorized)

        val result = worker().doWork()

        // Ein erneuter Versuch hülfe nicht, solange die Zugangsdaten ungültig sind.
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(NotificationChannels.SYNC_STATUS, posted.single().channelId)
    }

    @Test
    fun `Netzwerkfehler fuehrt zu einem erneuten Versuch`() = runTest {
        source.result = Outcome.Failure(AppError.NoConnection)

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertTrue(posted.isEmpty())
    }

    @Test
    fun `ohne eingerichtete Instanz passiert nichts`() = runTest {
        instanceStore.remove(instance.id)
        source.result = Outcome.Success(listOf(testComment(1)))

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(posted.isEmpty())
    }
}
