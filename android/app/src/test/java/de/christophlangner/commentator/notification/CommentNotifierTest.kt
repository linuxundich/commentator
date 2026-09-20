package de.christophlangner.commentator.notification

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.fake.testComment
import de.christophlangner.commentator.fake.testInstance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Die Vorgabe ist eindeutig: getrennte Kanäle und keine Doppelmeldung für
 * denselben Kommentar. Beides wird hier festgenagelt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "de-rDE")
class CommentNotifierTest {

    private lateinit var context: Context
    private lateinit var notifier: CommentNotifier
    private lateinit var manager: NotificationManager

    private val instance = testInstance()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val channels = NotificationChannels(context)
        channels.ensureCreated()
        notifier = CommentNotifier(context, channels)
        manager = context.getSystemService(NotificationManager::class.java)
    }

    @Test
    fun `alle Kanaele werden angelegt`() {
        listOf(
            NotificationChannels.NEW_COMMENTS,
            NotificationChannels.MODERATION_EVENTS,
            NotificationChannels.SYNC_STATUS,
        ).forEach { id ->
            assertNotNull("Kanal $id fehlt", manager.getNotificationChannel(id))
        }
    }

    @Test
    fun `neuer Wortbeitrag landet im Kanal fuer neue Kommentare`() {
        notifier.notifyNewComments(instance, listOf(testComment(1, parentId = 0)))

        val posted = shadowOf(manager).allNotifications.single()
        assertEquals(NotificationChannels.NEW_COMMENTS, posted.channelId)
    }

    @Test
    fun `Antwort landet im Kanal fuer Antworten`() {
        notifier.notifyNewComments(instance, listOf(testComment(2, parentId = 1)))

        val posted = shadowOf(manager).allNotifications.single()
        assertEquals(NotificationChannels.MODERATION_EVENTS, posted.channelId)
    }

    @Test
    fun `derselbe Kommentar erzeugt keine zweite Benachrichtigung`() {
        val comment = testComment(7)

        notifier.notifyNewComments(instance, listOf(comment))
        notifier.notifyNewComments(instance, listOf(comment))

        // Die Kennung leitet sich fest aus der Kommentar-ID ab, deshalb
        // ersetzt die zweite Meldung die erste.
        assertEquals(1, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun `mehrere Kommentare bekommen eine Sammelmeldung`() {
        notifier.notifyNewComments(
            instance,
            listOf(testComment(1), testComment(2), testComment(3)),
        )

        // Drei Einzelmeldungen plus eine Zusammenfassung.
        assertEquals(4, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun `ein einzelner Kommentar bekommt keine Sammelmeldung`() {
        notifier.notifyNewComments(instance, listOf(testComment(1)))

        assertEquals(1, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun `Hinweis auf ungueltige Anmeldung nutzt den Synchronisierungskanal`() {
        notifier.notifySessionInvalid(instance)

        val posted = shadowOf(manager).allNotifications.single()
        assertEquals(NotificationChannels.SYNC_STATUS, posted.channelId)

        notifier.clearSessionInvalid()
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    @Test
    fun `ohne Berechtigung wird nichts gemeldet`() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        notifier.notifyNewComments(instance, listOf(testComment(1, status = CommentStatus.PENDING)))

        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
}
