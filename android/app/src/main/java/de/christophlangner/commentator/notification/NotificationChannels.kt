package de.christophlangner.commentator.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import de.christophlangner.commentator.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Legt die Benachrichtigungskanäle an.
 *
 * Getrennte Kanäle, damit der Benutzer in den Android-Systemeinstellungen
 * einzeln entscheiden kann, was ihn erreichen soll: neue Kommentare sind
 * etwas anderes als eine Statusmeldung der Synchronisierung.
 */
@Singleton
class NotificationChannels @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun ensureCreated() {
        val manager = context.getSystemService<NotificationManager>() ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                NEW_COMMENTS,
                context.getString(R.string.channel_new_comments),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.channel_new_comments_description)
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                MODERATION_EVENTS,
                context.getString(R.string.channel_moderation_events),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_moderation_events_description)
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                SYNC_STATUS,
                context.getString(R.string.channel_sync_status),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_sync_status_description)
            },
        )
    }

    fun areNotificationsEnabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    companion object {
        const val NEW_COMMENTS = "new_comments"
        const val MODERATION_EVENTS = "moderation_events"
        const val SYNC_STATUS = "sync_status"
    }
}
