package de.christophlangner.commentator.domain.usecase

import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.core.net.ConnectivityObserver
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.model.ModerationAction
import de.christophlangner.commentator.domain.repository.CommentAlerts
import de.christophlangner.commentator.domain.repository.CommentRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Führt eine Moderationsaktion aus.
 *
 * Zwei Dinge passieren hier und nicht im Repository, weil sie fachliche
 * Regeln sind: Ohne Verbindung wird gar nicht erst geschrieben, und für die
 * Rückgängig-Funktion wird der vorherige Status ermittelt, bevor er
 * überschrieben wird.
 */
class ModerateCommentUseCase @Inject constructor(
    private val repository: CommentRepository,
    private val connectivity: ConnectivityObserver,
    private val alerts: CommentAlerts = CommentAlerts.NONE,
) {

    data class Result(
        /** Status vor der Aktion – Grundlage für „Rückgängig“. */
        val previousStatus: CommentStatus,
        /** Endgültiges Löschen lässt sich nicht zurücknehmen. */
        val undoable: Boolean,
    )

    suspend operator fun invoke(
        instanceId: String,
        commentId: Long,
        action: ModerationAction,
    ): Outcome<Result> {
        if (!connectivity.isCurrentlyOnline()) {
            return Outcome.Failure(AppError.OfflineWriteBlocked)
        }

        val previous = repository.observeComment(instanceId, commentId).first()?.status
            ?: return Outcome.Failure(AppError.NotFound)

        return when (val outcome = repository.moderate(instanceId, commentId, action)) {
            is Outcome.Success -> {
                // Erledigt ist erledigt - auch dann, wenn die Aktion später
                // zurückgenommen wird: Wer zurücknimmt, steht gerade vor dem
                // Kommentar und braucht keinen Hinweis darauf.
                alerts.dismiss(instanceId, commentId)
                Outcome.Success(
                Result(
                    previousStatus = previous,
                    undoable = action !is ModerationAction.Delete || !action.permanent,
                ),
            )
            }

            is Outcome.Failure -> outcome
        }
    }
}
