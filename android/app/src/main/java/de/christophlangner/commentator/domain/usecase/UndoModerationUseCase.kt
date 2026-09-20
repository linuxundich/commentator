package de.christophlangner.commentator.domain.usecase

import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.core.net.ConnectivityObserver
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.repository.CommentRepository
import javax.inject.Inject

/** Setzt den Status zurück, den ein Kommentar vor einer Moderationsaktion hatte. */
class UndoModerationUseCase @Inject constructor(
    private val repository: CommentRepository,
    private val connectivity: ConnectivityObserver,
) {
    suspend operator fun invoke(
        instanceId: String,
        commentId: Long,
        previousStatus: CommentStatus,
    ): Outcome<Unit> {
        if (!connectivity.isCurrentlyOnline()) {
            return Outcome.Failure(AppError.OfflineWriteBlocked)
        }
        return repository.restoreStatus(instanceId, commentId, previousStatus)
    }
}
