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
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.model.WordPressInstance
import de.christophlangner.commentator.fake.FakeCommentDao
import de.christophlangner.commentator.fake.FakeCommentRepository
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
    private val repository = FakeCommentRepository()

    private val instance = testInstance()

    /**
     * Liefert vorgegebene Kommentare und merkt sich, was gemeldet wurde.
     *
     * Der Ausgangszustand wird je Blog geführt - wie in der echten
     * Umsetzung. Waere er gemeinsam, koennte ein zweiter Blog nie einen
     * ersten Lauf haben.
     */
    private class FakeNewCommentSource : NewCommentSource {
        /** Was jeder Blog liefert, sofern für ihn nichts Eigenes hinterlegt ist. */
        var result: Outcome<NewComments> = Outcome.Success(NewComments.NONE)

        /** Abweichendes Ergebnis je Blog. */
        val resultFor = mutableMapOf<String, Outcome<NewComments>>()

        val notified = mutableListOf<Comment>()
        val notifiedPerInstance = mutableMapOf<String, MutableList<Comment>>()

        /** Welche Blogs abgefragt wurden, in der Reihenfolge der Aufrufe. */
        val fetched = mutableListOf<String>()

        private val baselines = mutableSetOf<String>()

        override suspend fun fetchUnnotified(instance: WordPressInstance): Outcome<NewComments> {
            fetched += instance.id
            return resultFor[instance.id] ?: result
        }

        override suspend fun markNotified(instance: WordPressInstance, comments: List<Comment>) {
            notified += comments
            notifiedPerInstance.getOrPut(instance.id) { mutableListOf() } += comments
            // Wie die echte Implementierung: Auch eine leere Liste setzt den
            // Ausgangszustand.
            baselines += instance.id
        }

        override suspend fun hasBaseline(instance: WordPressInstance) = instance.id in baselines

        /** Setzt den Ausgangszustand, ohne zu melden - fuer Tests, die darauf aufbauen. */
        fun markBaseline(vararg instanceIds: String) {
            baselines += instanceIds
        }
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
                    repository,
                    SiteIconLoader(appContext),
                )
            })
            .build()

    private val posted get() = shadowOf(
        context.getSystemService(NotificationManager::class.java),
    ).allNotifications

    @Test
    fun `erster Lauf meldet nichts, sondern setzt den Ausgangszustand`() = runTest {
        source.result = Outcome.Success(
            NewComments(toReport = listOf(testComment(1), testComment(2))),
        )

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        // Sonst käme beim ersten Start der gesamte Rückstand als Schwall.
        assertTrue("Beim ersten Lauf darf nichts gemeldet werden", posted.isEmpty())
        assertEquals(2, source.notified.size)
    }

    @Test
    fun `nach gesetztem Ausgangszustand wird gemeldet`() = runTest {
        source.result = Outcome.Success(NewComments(toReport = listOf(testComment(1))))
        worker().doWork()
        source.notified.clear()

        source.result = Outcome.Success(
            NewComments(toReport = listOf(testComment(5, author = "Neue Leserin"))),
        )
        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, posted.size)
        assertEquals(NotificationChannels.NEW_COMMENTS, posted.single().channelId)
        assertEquals(1, source.notified.size)
    }

    @Test
    fun `erster Lauf ohne offene Kommentare setzt trotzdem den Ausgangszustand`() = runTest {
        source.result = Outcome.Success(NewComments.NONE)
        worker().doWork()

        // Ohne diesen Schritt gälte der nächste Lauf erneut als erster und die
        // erste echte Benachrichtigung bliebe aus.
        source.result = Outcome.Success(NewComments(toReport = listOf(testComment(3))))
        worker().doWork()

        assertEquals(1, posted.size)
    }

    @Test
    fun `abgeschaltete Benachrichtigungen unterbinden jede Meldung`() = runTest {
        settings.setNotificationsEnabled(false)
        source.result = Outcome.Success(NewComments(toReport = listOf(testComment(9))))

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(posted.isEmpty())
        assertTrue("Ohne Benachrichtigungen darf auch nichts vermerkt werden", source.notified.isEmpty())
    }

    @Test
    fun `stumme Rollen werden vermerkt, aber nicht gemeldet`() = runTest {
        // Vermerkt werden muessen sie trotzdem: Sonst gaelten sie beim
        // naechsten Lauf erneut als neu und wuerden jedes Mal erneut geholt.
        source.result = Outcome.Success(NewComments(toReport = listOf(testComment(1))))
        worker().doWork()
        source.notified.clear()

        source.result = Outcome.Success(
            NewComments(
                toReport = listOf(testComment(5)),
                muted = listOf(testComment(6, author = "Redaktion")),
            ),
        )
        worker().doWork()

        assertEquals(1, posted.size)
        assertEquals(2, source.notified.size)
    }

    @Test
    fun `ein Lauf mit ausschliesslich stummen Kommentaren meldet nichts`() = runTest {
        source.result = Outcome.Success(NewComments(toReport = listOf(testComment(1))))
        worker().doWork()
        source.notified.clear()

        source.result = Outcome.Success(NewComments(muted = listOf(testComment(7))))
        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(posted.isEmpty())
        assertEquals(1, source.notified.size)
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
        source.result = Outcome.Success(NewComments(toReport = listOf(testComment(1))))

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(posted.isEmpty())
    }

    // --- Mehrere Blogs ---

    /**
     * Richtet einen zweiten Blog ein.
     *
     * Beide bekommen einen Ausgangszustand, damit die Tests danach das
     * Melden prüfen und nicht den ersten Lauf.
     */
    private suspend fun zweitblog(): WordPressInstance {
        val zweiter = testInstance(id = "instance-2").copy(displayName = "Zweitblog")
        instanceStore.upsert(zweiter, makeActive = false)
        source.markBaseline(instance.id, zweiter.id)
        return zweiter
    }

    @Test
    fun `ein Lauf geht alle Blogs durch`() = runTest {
        val zweiter = zweitblog()
        source.resultFor[instance.id] =
            Outcome.Success(NewComments(toReport = listOf(testComment(1))))
        source.resultFor[zweiter.id] =
            Outcome.Success(NewComments(toReport = listOf(testComment(2))))

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(listOf(instance.id, zweiter.id), source.fetched)
        // Beide Meldungen stehen da: Kommentar-IDs sind nur innerhalb eines
        // Blogs eindeutig, und ohne Unterscheidung wuerde die eine die andere
        // ersetzen.
        assertEquals(2, posted.size)
    }

    @Test
    fun `gleiche Kommentar-ID auf zwei Blogs ergibt zwei Meldungen`() = runTest {
        // Der Fehler, den das absichert: Die Kennung der Benachrichtigung kam
        // allein aus der Kommentar-ID. Auf zwei Blogs gibt es je einen
        // Kommentar 1 - der zweite hätte den ersten überschrieben.
        val zweiter = zweitblog()
        source.resultFor[instance.id] =
            Outcome.Success(NewComments(toReport = listOf(testComment(1))))
        source.resultFor[zweiter.id] =
            Outcome.Success(NewComments(toReport = listOf(testComment(1))))

        worker().doWork()

        assertEquals(2, posted.size)
    }

    @Test
    fun `ein stummgeschalteter Blog wird uebersprungen`() = runTest {
        val zweiter = zweitblog()
        settings.setSiteNotificationsEnabled(zweiter.id, false)
        source.result = Outcome.Success(NewComments(toReport = listOf(testComment(1))))

        worker().doWork()

        // Gar nicht abgefragt: Ein stiller Blog soll nicht einmal Daten kosten.
        assertEquals(listOf(instance.id), source.fetched)
        assertEquals(1, posted.size)
    }

    @Test
    fun `der Hauptschalter schweigt alle Blogs`() = runTest {
        zweitblog()
        settings.setNotificationsEnabled(false)
        source.result = Outcome.Success(NewComments(toReport = listOf(testComment(1))))

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(source.fetched.isEmpty())
        assertTrue(posted.isEmpty())
    }

    @Test
    fun `abgelehnte Zugangsdaten eines Blogs halten die anderen nicht auf`() = runTest {
        val zweiter = zweitblog()
        source.resultFor[instance.id] = Outcome.Failure(AppError.Unauthorized)
        source.resultFor[zweiter.id] =
            Outcome.Success(NewComments(toReport = listOf(testComment(2))))

        val result = worker().doWork()

        // Kein erneuter Versuch: Daran ändert ein Wiederholen nichts.
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(listOf(instance.id, zweiter.id), source.fetched)
        // Der Hinweis auf die Anmeldung und die Meldung des zweiten Blogs.
        assertEquals(2, posted.size)
        assertTrue(
            posted.any { it.channelId == NotificationChannels.SYNC_STATUS },
        )
        assertEquals(listOf(2L), source.notifiedPerInstance[zweiter.id]?.map { it.id })
    }

    @Test
    fun `ein unerreichbarer Blog haelt die Meldungen der anderen nicht zurueck`() = runTest {
        val zweiter = zweitblog()
        source.resultFor[instance.id] = Outcome.Failure(AppError.NoConnection)
        source.resultFor[zweiter.id] =
            Outcome.Success(NewComments(toReport = listOf(testComment(2))))

        val result = worker().doWork()

        // Wiederholt wird der Durchgang schon - aber erst, nachdem der zweite
        // Blog abgearbeitet war.
        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(listOf(instance.id, zweiter.id), source.fetched)
        assertEquals(1, posted.size)
    }

    @Test
    fun `bei mehreren Blogs nennt die Meldung den Blog`() = runTest {
        val zweiter = zweitblog()
        source.resultFor[zweiter.id] =
            Outcome.Success(NewComments(toReport = listOf(testComment(2))))

        worker().doWork()

        // Ohne den Namen sagt die Meldung nicht, wo der Kommentar steht.
        val meldung = posted.single()
        assertTrue(
            "Unterzeile war: ${meldung.extras.getCharSequence("android.subText")}",
            meldung.extras.getCharSequence("android.subText")
                ?.contains("Zweitblog") == true,
        )
    }

    @Test
    fun `bei einem einzigen Blog bleibt der Name aus der Unterzeile`() = runTest {
        source.markBaseline(instance.id)
        source.result = Outcome.Success(NewComments(toReport = listOf(testComment(1))))

        worker().doWork()

        // Bei nur einem Blog waere der Name in jeder Meldung dieselbe Zeile -
        // und damit nichts als Rauschen.
        val unterzeile = posted.single().extras.getCharSequence("android.subText")
        assertTrue(
            "Unterzeile war: $unterzeile",
            unterzeile == null || !unterzeile.contains("Testblog"),
        )
    }

    @Test
    fun `im Web erledigte Kommentare verlieren ihre Meldung`() = runTest {
        source.markBaseline(instance.id)
        notifier.notifyNewComments(
            instance,
            listOf(
                testComment(4, status = CommentStatus.PENDING),
                testComment(5, status = CommentStatus.PENDING),
                testComment(6, status = CommentStatus.PENDING),
            ),
        )
        // 4 wurde im Web freigegeben, 5 steht noch aus, 6 ist gelöscht.
        repository.comments.value = listOf(
            testComment(4, status = CommentStatus.APPROVED),
            testComment(5, status = CommentStatus.PENDING),
        )

        worker().doWork()

        assertEquals(setOf(5L), notifier.openAlerts(instance.id).keys)
    }

    @Test
    fun `freigegeben gemeldete Kommentare bleiben stehen`() = runTest {
        source.markBaseline(instance.id)
        notifier.notifyNewComments(instance, listOf(testComment(4, status = CommentStatus.APPROVED)))
        repository.comments.value = listOf(testComment(4, status = CommentStatus.APPROVED))

        worker().doWork()

        // Er wartet womöglich noch auf eine Antwort.
        assertEquals(setOf(4L), notifier.openAlerts(instance.id).keys)
    }
}
