package de.christophlangner.commentator.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import de.christophlangner.commentator.MainActivity
import de.christophlangner.commentator.R
import de.christophlangner.commentator.domain.model.Comment
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.model.WordPressInstance
import de.christophlangner.commentator.domain.repository.CommentAlerts
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Erzeugt die Benachrichtigungen.
 *
 * Die Kennung einer Benachrichtigung leitet sich fest aus der Kommentar-ID
 * ab. Würde derselbe Kommentar ein zweites Mal gemeldet, ersetzt er damit
 * seine eigene Benachrichtigung, statt eine weitere zu erzeugen.
 *
 * Kommentar-IDs sind aber nur innerhalb eines Blogs eindeutig: Auf zwei Blogs
 * gibt es je einen Kommentar 5. Dazu kommt der Blog als Marke (`tag`) - eine
 * zweite, freie Dimension neben der Zahl. Sie über einen Hashwert in die Zahl
 * zu falten hätte Zusammenstöße nur unwahrscheinlicher gemacht, nicht
 * unmöglich, und ein Zusammenstoß hieße: Blog B überschreibt die Meldung von
 * Blog A.
 */
@Singleton
class CommentNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channels: NotificationChannels,
) : CommentAlerts {

    override fun dismiss(instanceId: String, commentId: Long) = dismissComment(instanceId, commentId)

    /**
     * Meldet neue Kommentare eines Blogs.
     *
     * [showSiteName] nennt den Blog in der Unterzeile. Der Aufrufer
     * entscheidet das, weil nur er weiß, ob überhaupt mehrere eingerichtet
     * sind.
     */
    fun notifyNewComments(
        instance: WordPressInstance,
        comments: List<Comment>,
        showSiteName: Boolean = false,
        siteIcon: Bitmap? = null,
    ) {
        if (comments.isEmpty() || !canPost()) return
        channels.ensureCreated()

        val group = groupKey(instance.id)

        comments.forEach { comment ->
            val notification = commentNotification(instance, comment, showSiteName, siteIcon)

            post(instance.id, notificationId(comment.id), notification)
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
                .setLargeIcon(siteIcon)
                .setContentIntent(openInboxIntent(instance.id))
                .setGroup(group)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .build()

            post(instance.id, SUMMARY_ID, summary)
        }
    }

    /**
     * Die Meldung zu einem einzelnen Kommentar.
     *
     * [failure] steht nach einer gescheiterten Aktion aus der Benachrichtigung
     * über dem Kommentartext; [lostReply] ist dann die nicht zugestellte
     * Antwort, damit sie sich von Hand übernehmen lässt.
     */
    private fun commentNotification(
        instance: WordPressInstance,
        comment: Comment,
        showSiteName: Boolean,
        siteIcon: Bitmap?,
        failure: String? = null,
        lostReply: String? = null,
    ): Notification {
        val body = listOfNotNull(
            failure,
            lostReply?.let { context.getString(R.string.notification_lost_reply, it) },
            comment.contentPlain,
        ).joinToString("\n\n")

        // Eine Antwort auf einen bestehenden Kommentar ist eine andere Sorte
        // Ereignis als ein neuer Wortbeitrag. Beide bekommen deshalb einen
        // eigenen Kanal, den der Benutzer getrennt steuern kann.
        val channel = if (comment.isReply) {
            NotificationChannels.MODERATION_EVENTS
        } else {
            NotificationChannels.NEW_COMMENTS
        }

        val builder = NotificationCompat.Builder(context, channel)
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
            .setContentText(failure ?: comment.contentPlain.take(SNIPPET_LENGTH))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSubText(
                // Der Beitragstitel sagt, wo der Kommentar steht - bei
                // mehreren Blogs ist das erst mit dem Blog davor eine
                // vollständige Antwort.
                listOfNotNull(
                    instance.displayName.takeIf { showSiteName },
                    comment.postTitle,
                ).joinToString(" · ").takeIf { it.isNotEmpty() },
            )
            // Das Symbol des Blogs: Bei mehreren Blogs sagt es auf einen
            // Blick, wohin die Meldung gehört.
            .setLargeIcon(siteIcon)
            .setContentIntent(openCommentIntent(instance.id, comment.id))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setGroup(groupKey(instance.id))
            .setWhen(comment.date.toEpochMilli())
            .setShowWhen(true)
            // Ein Fehlschlag soll nicht noch einmal klingeln.
            .setOnlyAlertOnce(failure != null)
            // Der Status zum Zeitpunkt der Meldung. Daran entscheidet der
            // Abgleich, ob die Meldung durch eine Moderation im Web
            // überholt ist.
            .addExtras(Bundle().apply { putString(EXTRA_STATUS, comment.status.name) })

        actionsFor(instance, comment).forEach(builder::addAction)
        return builder.build()
    }

    /**
     * Die Knöpfe unter einem Kommentar.
     *
     * Nur für Konten, die moderieren dürfen - sonst schlüge jeder Tipp fehl.
     * „Freigeben" nur, wo es etwas freizugeben gibt; auf Blogs, die selbst
     * freischalten, stünde der Knopf sonst unter jedem Kommentar.
     */
    private fun actionsFor(
        instance: WordPressInstance,
        comment: Comment,
    ): List<NotificationCompat.Action> {
        if (!instance.canModerate) return emptyList()
        val pending = comment.status == CommentStatus.PENDING

        val replyInput = RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY_TEXT)
            .setLabel(context.getString(R.string.notification_reply_hint, comment.authorName))
            .build()

        return listOfNotNull(
            NotificationCompat.Action.Builder(
                R.drawable.ic_notification,
                context.getString(
                    if (pending) {
                        R.string.notification_action_approve_and_reply
                    } else {
                        R.string.notification_action_reply
                    },
                ),
                actionIntent(instance.id, comment.id, NotificationAction.REPLY, mutable = true),
            )
                .addRemoteInput(replyInput)
                .setAllowGeneratedReplies(false)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .setShowsUserInterface(false)
                .build(),
            NotificationCompat.Action.Builder(
                R.drawable.ic_notification,
                context.getString(R.string.notification_action_approve),
                actionIntent(instance.id, comment.id, NotificationAction.APPROVE),
            )
                .setShowsUserInterface(false)
                .build()
                .takeIf { pending },
            NotificationCompat.Action.Builder(
                R.drawable.ic_notification,
                context.getString(R.string.notification_action_spam),
                actionIntent(instance.id, comment.id, NotificationAction.SPAM),
            )
                .setShowsUserInterface(false)
                .build()
                .takeIf { comment.status != CommentStatus.SPAM },
        )
    }

    /**
     * Absicht für einen Knopf. Die Eingabe einer Antwort braucht eine
     * veränderliche Absicht - das System trägt den Text nachträglich ein.
     */
    private fun actionIntent(
        instanceId: String,
        commentId: Long,
        action: NotificationAction,
        mutable: Boolean = false,
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction("${context.packageName}.$action")
            // Absichten unterscheiden sich für das System nicht an ihren
            // Extras. Ohne eigene Adresse könnten sich zwei Kommentare mit
            // zufällig gleicher Anforderungskennung ihre Knöpfe teilen.
            .setData("$DEEP_LINK_SCHEME://action/$instanceId/$commentId".toUri())
            .putExtra(NotificationActionReceiver.EXTRA_ACTION, action.name)
            .putExtra(NotificationActionReceiver.EXTRA_INSTANCE_ID, instanceId)
            .putExtra(NotificationActionReceiver.EXTRA_COMMENT_ID, commentId)

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (mutable) {
            PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(instanceId, commentId) + action.ordinal + 1,
            intent,
            flags,
        )
    }

    /**
     * Ersetzt die Meldung eines Kommentars durch eine mit Fehlerhinweis.
     *
     * Die Knöpfe bleiben, damit sich die Aktion mit einem Tipp wiederholen
     * lässt, sobald etwa die Verbindung zurück ist.
     */
    fun notifyActionFailed(
        instance: WordPressInstance,
        comment: Comment,
        action: NotificationAction,
        reason: String,
        replyText: String? = null,
        siteIcon: Bitmap? = null,
    ) {
        if (!canPost()) return
        channels.ensureCreated()

        val failure = context.getString(
            when (action) {
                NotificationAction.APPROVE -> R.string.notification_failed_approve
                NotificationAction.SPAM -> R.string.notification_failed_spam
                NotificationAction.REPLY -> R.string.notification_failed_reply
            },
            reason,
        )
        post(
            instance.id,
            notificationId(comment.id),
            commentNotification(
                instance = instance,
                comment = comment,
                showSiteName = false,
                siteIcon = siteIcon,
                failure = failure,
                lostReply = replyText.takeIf { action == NotificationAction.REPLY },
            ),
        )
    }

    /**
     * Nimmt die Meldung eines Kommentars zurück, sobald er erledigt ist.
     *
     * Bleibt danach keine Einzelmeldung des Blogs übrig, geht auch die
     * Zusammenfassung - sonst stünde dort „3 neue Kommentare" über nichts.
     */
    fun dismissComment(instanceId: String, commentId: Long, afterDirectReply: Boolean = false) {
        val manager = NotificationManagerCompat.from(context)
        val id = notificationId(commentId)

        if (afterDirectReply) {
            // Nach einer Antwort direkt aus der Benachrichtigung hält Android
            // (ab 15) die Meldung fest und übergeht ein bloßes Zurücknehmen.
            // Eine Aktualisierung löst sie; die bestätigt kurz und verschwindet
            // dann von selbst.
            confirmAndExpire(instanceId, id)
        } else {
            manager.cancel(instanceId, id)
        }

        val remaining = manager.activeNotifications.count {
            // Die eben erledigte zählt nicht mit, auch wenn sie als kurze
            // Bestätigung noch steht.
            it.tag == instanceId && it.id > 0 && it.id != id
        }
        if (remaining == 0) manager.cancel(instanceId, SUMMARY_ID)
    }

    private fun confirmAndExpire(instanceId: String, id: Int) {
        if (!canPost()) {
            NotificationManagerCompat.from(context).cancel(instanceId, id)
            return
        }
        val done = NotificationCompat.Builder(context, NotificationChannels.NEW_COMMENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_action_done))
            .setGroup(groupKey(instanceId))
            .setSilent(true)
            .setTimeoutAfter(CONFIRMATION_MILLIS)
            .build()
        post(instanceId, id, done)
    }

    /** Bestätigt, dass ein Testweckruf über UnifiedPush angekommen ist. */
    fun notifyPushTest(instance: WordPressInstance) {
        if (!canPost()) return
        channels.ensureCreated()
        val notification = NotificationCompat.Builder(context, NotificationChannels.SYNC_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_push_test_title))
            .setContentText(context.getString(R.string.notification_push_test_text, instance.displayName))
            .setContentIntent(openInboxIntent(instance.id))
            .setAutoCancel(true)
            .setTimeoutAfter(PUSH_TEST_MILLIS)
            .build()
        post(instance.id, PUSH_TEST_ID, notification)
    }

    /**
     * Die offenen Meldungen eines Blogs: Kommentar-ID und der Status, mit
     * dem sie gemeldet wurden.
     */
    fun openAlerts(instanceId: String): Map<Long, CommentStatus?> =
        NotificationManagerCompat.from(context).activeNotifications
            .filter { it.tag == instanceId && it.id > 0 }
            .associate { active ->
                active.id.toLong() to active.notification.extras.getString(EXTRA_STATUS)
                    ?.let { name -> CommentStatus.entries.firstOrNull { it.name == name } }
            }

    /**
     * Räumt Meldungen weg, die durch eine Entscheidung anderswo überholt sind.
     *
     * [current] ist der Stand vom Blog; was dort fehlt, ist gelöscht. Weg
     * kommt, was gelöscht, Spam oder im Papierkorb ist, und was als offen
     * gemeldet wurde und inzwischen freigegeben ist. Ein Kommentar, der schon
     * freigegeben gemeldet wurde, bleibt: Er wartet womöglich auf eine
     * Antwort, und die gibt es erst, wenn jemand ihn ansieht.
     */
    fun reconcile(instanceId: String, current: Map<Long, CommentStatus>) {
        openAlerts(instanceId).forEach { (commentId, notifiedAs) ->
            val now = current[commentId]
            val overtaken = now == null ||
                now == CommentStatus.SPAM ||
                now == CommentStatus.TRASH ||
                (notifiedAs == CommentStatus.PENDING && now != CommentStatus.PENDING)
            if (overtaken) dismissComment(instanceId, commentId)
        }
    }

    /** Kurzer Hinweis, solange eine Aktion läuft - nur bis Android 11 sichtbar. */
    fun actionInProgress(): Notification = progress(R.string.notification_action_running)

    /** Kurzer Hinweis während einer per Push angestoßenen Prüfung - nur bis Android 11. */
    fun syncInProgress(): Notification = progress(R.string.notification_sync_running)

    private fun progress(title: Int): Notification {
        channels.ensureCreated()
        return NotificationCompat.Builder(context, NotificationChannels.SYNC_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(title))
            .setSilent(true)
            .build()
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
            .setContentIntent(openInboxIntent(instance.id))
            .setOngoing(false)
            .setAutoCancel(true)
            .build()

        post(instance.id, SESSION_INVALID_ID, notification)
    }

    fun clearSessionInvalid(instance: WordPressInstance) {
        NotificationManagerCompat.from(context).cancel(instance.id, SESSION_INVALID_ID)
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
            // Die Anforderungskennung unterscheidet die Absichten
            // untereinander. Nur die Kommentar-ID würde zwei Blogs mit
            // demselben Kommentar dieselbe Absicht geben - und damit beide
            // Benachrichtigungen in denselben Kommentar führen.
            requestCode(instanceId, commentId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Öffnet die App beim betreffenden Blog.
     *
     * Ohne die Kennung landete ein Hinweis zu Blog B im Posteingang von Blog
     * A - dort, wo nichts zu sehen ist, wovon der Hinweis spricht.
     */
    private fun openInboxIntent(instanceId: String): PendingIntent {
        val intent = Intent(
            Intent.ACTION_VIEW,
            "$DEEP_LINK_SCHEME://inbox/$instanceId".toUri(),
            context,
            MainActivity::class.java,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        return PendingIntent.getActivity(
            context,
            requestCode(instanceId, 0),
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
    private fun post(tag: String, id: Int, notification: Notification) {
        NotificationManagerCompat.from(context).notify(tag, id, notification)
    }

    private fun notificationId(commentId: Long): Int = (commentId % Int.MAX_VALUE).toInt()

    private fun requestCode(instanceId: String, commentId: Long): Int =
        (instanceId + "/" + commentId).hashCode()

    private fun groupKey(instanceId: String) = "new_comments_$instanceId"

    companion object {
        const val DEEP_LINK_SCHEME = "commentator"
        private const val CONFIRMATION_MILLIS = 2_000L
        private const val EXTRA_STATUS = "de.christophlangner.commentator.STATUS"
        private const val SNIPPET_LENGTH = 120

        /**
         * Feste Zahlen, eindeutig erst zusammen mit der Marke des Blogs.
         *
         * Die Zusammenfassung und der Sitzungshinweis gibt es je Blog genau
         * einmal; welcher gemeint ist, sagt die Marke.
         */
        private const val SESSION_INVALID_ID = -1
        private const val SUMMARY_ID = -2
        private const val PUSH_TEST_ID = -3
        private const val PUSH_TEST_MILLIS = 60_000L
    }
}
