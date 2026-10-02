package de.christophlangner.commentator.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf

/**
 * Nimmt einen Tipp auf einen Knopf in der Benachrichtigung entgegen.
 *
 * Er schreibt selbst nichts, sondern reicht an [NotificationActionWorker]
 * weiter: Ein Empfänger hat nur wenige Sekunden, ein langsamer Blog braucht
 * unter Umständen länger. Die Arbeit läuft beschleunigt, also so bald wie
 * möglich und nicht erst, wenn das System gerade Zeit hat.
 *
 * Je Kommentar gibt es höchstens eine laufende Aktion. Ein zweiter Tipp,
 * während die erste noch unterwegs ist, wird verworfen - zweimal Freigeben
 * hätte keinen Sinn, und zwei Antworten wären zwei Kommentare im Blog.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION)
            ?.let { name -> NotificationAction.entries.firstOrNull { it.name == name } }
            ?: return
        val instanceId = intent.getStringExtra(EXTRA_INSTANCE_ID) ?: return
        val commentId = intent.getLongExtra(EXTRA_COMMENT_ID, 0L).takeIf { it > 0 } ?: return

        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_REPLY_TEXT)
            ?.toString()
            ?.trim()
        if (action == NotificationAction.REPLY && replyText.isNullOrEmpty()) return

        val request = OneTimeWorkRequestBuilder<NotificationActionWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(
                workDataOf(
                    NotificationActionWorker.KEY_ACTION to action.name,
                    NotificationActionWorker.KEY_INSTANCE_ID to instanceId,
                    NotificationActionWorker.KEY_COMMENT_ID to commentId,
                    NotificationActionWorker.KEY_REPLY_TEXT to replyText,
                ),
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "commentator-action-$instanceId-$commentId",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        const val EXTRA_ACTION = "action"
        const val EXTRA_INSTANCE_ID = "instance_id"
        const val EXTRA_COMMENT_ID = "comment_id"
        const val KEY_REPLY_TEXT = "reply_text"
    }
}

/** Was sich direkt aus der Benachrichtigung erledigen lässt. */
enum class NotificationAction { APPROVE, SPAM, REPLY }
