package de.christophlangner.commentator.notification

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.data.account.InstanceStore
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.model.ModerationAction
import de.christophlangner.commentator.domain.usecase.ModerateCommentUseCase
import de.christophlangner.commentator.domain.usecase.ReplyToCommentUseCase
import de.christophlangner.commentator.fake.FakeCommentRepository
import de.christophlangner.commentator.fake.FakeConnectivityObserver
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
 * Aktionen aus der Benachrichtigung: Erfolg räumt die Meldung weg, ein
 * Fehlschlag lässt sie mit Grund stehen. Still fehlschlagen darf nichts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "de-rDE")
class NotificationActionWorkerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var repository: FakeCommentRepository
    private lateinit var connectivity: FakeConnectivityObserver
    private lateinit var notifier: CommentNotifier
    private lateinit var instanceStore: InstanceStore

    private val instance = testInstance()

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        repository = FakeCommentRepository()
        connectivity = FakeConnectivityObserver()
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

    private fun worker(
        action: NotificationAction,
        commentId: Long,
        replyText: String? = null,
    ): NotificationActionWorker =
        TestListenableWorkerBuilder<NotificationActionWorker>(context)
            .setInputData(
                workDataOf(
                    NotificationActionWorker.KEY_ACTION to action.name,
                    NotificationActionWorker.KEY_INSTANCE_ID to instance.id,
                    NotificationActionWorker.KEY_COMMENT_ID to commentId,
                    NotificationActionWorker.KEY_REPLY_TEXT to replyText,
                ),
            )
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = NotificationActionWorker(
                    appContext,
                    workerParameters,
                    instanceStore,
                    repository,
                    ModerateCommentUseCase(repository, connectivity),
                    ReplyToCommentUseCase(repository, connectivity),
                    notifier,
                    SiteIconLoader(appContext),
                )
            })
            .build()

    private val posted get() = shadowOf(
        context.getSystemService(NotificationManager::class.java),
    ).allNotifications

    private fun bekannt(vararg ids: Long) {
        val comments = ids.map { testComment(it, status = CommentStatus.PENDING) }
        repository.comments.value = comments
        notifier.notifyNewComments(instance, comments)
    }

    @Test
    fun `Freigeben schreibt und raeumt die Meldung weg`() = runTest {
        bekannt(4)

        val result = worker(NotificationAction.APPROVE, 4).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(listOf(4L to ModerationAction.Approve), repository.moderated)
        assertTrue(posted.isEmpty())
    }

    @Test
    fun `Spam markiert als Spam`() = runTest {
        bekannt(4)

        worker(NotificationAction.SPAM, 4).doWork()

        assertEquals(listOf(4L to ModerationAction.MarkAsSpam), repository.moderated)
        assertTrue(posted.isEmpty())
    }

    @Test
    fun `Antwort gibt den offenen Kommentar frei und antwortet`() = runTest {
        bekannt(4)
        var gesendet: String? = null
        repository.replyResult = {
            gesendet = "ja"
            Outcome.Success(testComment(999, parentId = 4))
        }

        worker(NotificationAction.REPLY, 4, "Danke!").doWork()

        assertEquals(listOf(4L to ModerationAction.Approve), repository.moderated)
        assertEquals("ja", gesendet)
        // Nach einer Direktantwort steht kurz eine Bestätigung, die von selbst
        // verschwindet - Android übergeht dort ein bloßes Zurücknehmen.
        val bestaetigung = posted.single()
        assertEquals("Erledigt", bestaetigung.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(bestaetigung.timeoutAfter > 0)
    }

    @Test
    fun `ohne Verbindung bleibt die Meldung mit Grund stehen`() = runTest {
        bekannt(4)
        connectivity.online.value = false

        val result = worker(NotificationAction.APPROVE, 4).doWork()

        // Kein erneuter Versuch: Eine später still nachgeholte Moderation
        // könnte eine inzwischen anders getroffene Entscheidung überschreiben.
        assertEquals(ListenableWorker.Result.success(), result)
        val text = posted.single().extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
        assertTrue(text, text.startsWith("Freigeben fehlgeschlagen"))
    }

    @Test
    fun `im Web geloeschter Kommentar raeumt die Meldung weg`() = runTest {
        notifier.notifyNewComments(instance, listOf(testComment(4)))
        // Weder im Zwischenspeicher noch auf dem Blog.
        repository.comments.value = emptyList()

        worker(NotificationAction.APPROVE, 4).doWork()

        assertTrue(repository.moderated.isEmpty())
        assertTrue(posted.isEmpty())
    }

    @Test
    fun `abgelehnte Freigabe nennt den Grund`() = runTest {
        bekannt(4)
        repository.moderateResult = Outcome.Failure(AppError.Forbidden)

        worker(NotificationAction.APPROVE, 4).doWork()

        assertEquals(1, posted.size)
    }
}
