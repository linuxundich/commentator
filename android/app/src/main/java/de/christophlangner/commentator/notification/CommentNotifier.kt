package de.christophlangner.commentator.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import de.christophlangner.commentator.MainActivity
import de.christophlangner.commentator.R
import de.christophlangner.commentator.domain.model.Comment
import de.christophlangner.commentator.domain.model.WordPressInstance
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Erzeugt die Benachrichtigungen.
 *
 * Die Kennung einer Benachrichtigung leitet sich fest aus der Kommentar-ID
 * ab. Würde derselbe Kommentar ein zweites Mal gemeldet, ersetzt er damit
 * seine eigene Benachrichtigung, statt eine weitere zu erzeugen.
 */
@Singleton
class CommentNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channels: NotificationChannels,
) {

    fun notifyNewComments(instance: WordPressInstance, comments: List<Comment>) {
        if (comments.isEmpty() || !canPost()) return
        channels.ensureCreated()

        val group = groupKey(instance.id)

        comments.forEach { comment ->
            // Eine Antwort auf einen bestehenden Kommentar ist eine andere
            // Sorte Ereignis als ein neuer Wortbeitrag. Beide bekommen deshalb
            // einen eigenen Kanal, den der Benutzer getrennt steuern kann.
            val channel = if (comment.isReply) {
                NotificationChannels.MODERATION_EVENTS
            } else {
                NotificationChannels.NEW_COMMENTS
            }

            val notification = NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(
                    context.getString(
                        if (comment.isReply) {
                            R.string.notification_new_reply_title
                        } else {
                            R.string.notification_new_comment_title
                        },
                        comment.authorName,
                    ),
                )
                .setContentText(comment.contentPlain.take(SNIPPET_LENGTH))
                .setStyle(NotificationCompat.BigTextStyle().bigText(comment.contentPlain))
                .setSubText(comment.postTitle)
                .setContentIntent(openCommentIntent(instance.id, comment.id))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setGroup(group)
                .setWhen(comment.date.toEpochMilli())
                .setShowWhen(true)
                .build()

            post(notificationId(comment.id), notification)
        }

        if (comments.size > 1) {
            val summary = NotificationCompat.Builder(context, NotificationChannels.NEW_COMMENTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(
                    context.resources.getQuantityString(
                        R.plurals.notification_new_comments_summary,
                        comments.size,
                        comments.size,
                    ),
                )
                .setContentText(instance.displayName)
                .setContentIntent(openInboxIntent())
                .setGroup(group)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .build()

            post(summaryId(instance.id), summary)
        }
    }

    /**
     * Dauerhafter Hinweis, wenn der Server die Zugangsdaten ablehnt. Ohne ihn
     * würde die Hintergrundprüfung stillschweigend nichts mehr liefern.
     */
    fun notifySessionInvalid(instance: WordPressInstance) {
        if (!canPost()) return
        channels.ensureCreated()

        val notification = NotificationCompat.Builder(context, NotificationChannels.SYNC_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_session_invalid_title))
            .setContentText(
                context.getString(
                    R.string.notification_session_invalid_text,
                    instance.displayName,
                ),
            )
            .setContentIntent(openInboxIntent())
            .setOngoing(false)
            .setAutoCancel(true)
            .build()

        post(SESSION_INVALID_ID, notification)
    }

    fun clearSessionInvalid() {
        NotificationManagerCompat.from(context).cancel(SESSION_INVALID_ID)
    }

    private fun openCommentIntent(instanceId: String, commentId: Long): PendingIntent {
        val intent = Intent(
            Intent.ACTION_VIEW,
            "$DEEP_LINK_SCHEME://comment/$instanceId/$commentId".toUri(),
            context,
            MainActivity::class.java,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        return PendingIntent.getActivity(
            context,
            notificationId(commentId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openInboxIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Ob überhaupt benachrichtigt werden darf.
     *
     * Die Laufzeitberechtigung gibt es erst ab Android 13. Auf älteren
     * Versionen danach zu fragen würde fälschlich `abgelehnt` ergeben und
     * Benachrichtigungen vollständig unterdrücken.
     */
    private fun canPost(): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

        return permitted && channels.areNotificationsEnabled()
    }

    /**
     * Einzige Stelle, an der tatsächlich zugestellt wird.
     *
     * Die Berechtigung ist über [canPost] bereits geprüft; Lint kann das über
     * Methodengrenzen hinweg nicht nachvollziehen, deshalb die eng begrenzte
     * Unterdrückung genau hier.
     */
    @SuppressLint("MissingPermission")
    private fun post(id: Int, notification: Notification) {
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun notificationId(commentId: Long): Int = (commentId % Int.MAX_VALUE).toInt()

    private fun summaryId(instanceId: String): Int = instanceId.hashCode()

    private fun groupKey(instanceId: String) = "new_comments_$instanceId"

    companion object {
        const val DEEP_LINK_SCHEME = "commentator"
        private const val SNIPPET_LENGTH = 120
        private const val SESSION_INVALID_ID = -1
    }
}
