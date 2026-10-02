package de.christophlangner.commentator.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.christophlangner.commentator.core.AppLog
import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.data.account.InstanceStore
import de.christophlangner.commentator.domain.model.Comment
import de.christophlangner.commentator.domain.model.ModerationAction
import de.christophlangner.commentator.domain.repository.CommentRepository
import de.christophlangner.commentator.domain.usecase.ModerateCommentUseCase
import de.christophlangner.commentator.domain.usecase.ReplyToCommentUseCase
import de.christophlangner.commentator.ui.common.ErrorTexts
import kotlinx.coroutines.flow.first

/**
 * Führt eine Aktion aus der Benachrichtigung aus.
 *
 * Über dieselben Use Cases wie die Oberfläche, damit für beide Wege dieselben
 * Regeln gelten - etwa, dass ohne Verbindung nichts geschrieben wird.
 *
 * Es wird bewusst nicht wiederholt. Eine Moderation, die irgendwann später
 * still nachgeholt wird, könnte eine inzwischen im Web getroffene
 * Entscheidung überschreiben. Stattdessen bleibt die Benachrichtigung stehen
 * und nennt den Fehler; eine fehlgeschlagene Antwort steht mit ihrem Text
 * darin, damit nichts Getipptes verloren geht.
 */
@HiltWorker
class NotificationActionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val instanceStore: InstanceStore,
    private val repository: CommentRepository,
    private val moderate: ModerateCommentUseCase,
    private val reply: ReplyToCommentUseCase,
    private val notifier: CommentNotifier,
    private val siteIcons: SiteIconLoader,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val action = inputData.getString(KEY_ACTION)
            ?.let { name -> NotificationAction.entries.firstOrNull { it.name == name } }
            ?: return Result.success()
        val instanceId = inputData.getString(KEY_INSTANCE_ID) ?: return Result.success()
        val commentId = inputData.getLong(KEY_COMMENT_ID, 0L)
        val replyText = inputData.getString(KEY_REPLY_TEXT)

        val instance = instanceStore.byId(instanceId)
        if (instance == null) {
            // Der Blog wurde inzwischen entfernt; seine Meldung ist gegenstandslos.
            notifier.dismissComment(instanceId, commentId)
            return Result.success()
        }

        val comment = when (val loaded = loadComment(instanceId, commentId)) {
            is Outcome.Success -> loaded.value
            is Outcome.Failure -> {
                // Im Web gelöscht: nichts mehr zu tun. Jeder andere Fehler
                // lässt die Meldung stehen, sie bleibt damit antippbar.
                if (loaded.error == AppError.NotFound) {
                    notifier.dismissComment(instanceId, commentId)
                }
                return Result.success()
            }
        }

        val outcome: Outcome<*> = when (action) {
            NotificationAction.APPROVE ->
                moderate(instanceId, commentId, ModerationAction.Approve)

            NotificationAction.SPAM ->
                moderate(instanceId, commentId, ModerationAction.MarkAsSpam)

            NotificationAction.REPLY ->
                reply(instanceId, comment, replyText.orEmpty())
        }

        when (outcome) {
            is Outcome.Success -> notifier.dismissComment(
                instanceId,
                commentId,
                afterDirectReply = action == NotificationAction.REPLY,
            )

            is Outcome.Failure -> {
                AppLog.d("Aktion $action aus der Benachrichtigung fehlgeschlagen: ${outcome.error}")
                notifier.notifyActionFailed(
                    instance = instance,
                    comment = comment,
                    action = action,
                    reason = ErrorTexts.message(applicationContext.resources, outcome.error),
                    replyText = replyText,
                    siteIcon = siteIcons.load(instance),
                )
            }
        }
        return Result.success()
    }

    /**
     * Der Kommentar aus dem Zwischenspeicher, sonst frisch vom Blog.
     *
     * Die Hintergrundprüfung legt gemeldete Kommentare im Zwischenspeicher
     * ab; fehlt er dort, wurde der Speicher inzwischen geleert.
     */
    private suspend fun loadComment(instanceId: String, commentId: Long): Outcome<Comment> =
        repository.observeComment(instanceId, commentId).first()
            ?.let { Outcome.Success(it) }
            ?: repository.fetchComment(instanceId, commentId)

    /**
     * Nur bis Android 11 nötig: Dort läuft beschleunigte Arbeit als
     * Vordergrunddienst und braucht eine eigene, kurze Benachrichtigung.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(FOREGROUND_ID, notifier.actionInProgress())

    companion object {
        const val KEY_ACTION = "action"
        const val KEY_INSTANCE_ID = "instance_id"
        const val KEY_COMMENT_ID = "comment_id"
        const val KEY_REPLY_TEXT = "reply_text"

        private const val FOREGROUND_ID = 0x0C0FFEE
    }
}
