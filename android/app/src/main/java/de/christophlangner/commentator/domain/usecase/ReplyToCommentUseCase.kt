package de.christophlangner.commentator.domain.usecase

import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.core.net.ConnectivityObserver
import de.christophlangner.commentator.domain.model.Comment
import de.christophlangner.commentator.domain.repository.CommentRepository
import javax.inject.Inject

/**
 * Veröffentlicht eine Antwort als echten WordPress-Kommentar, der auf den
 * ursprünglichen Kommentar verweist.
 */
class ReplyToCommentUseCase @Inject constructor(
    private val repository: CommentRepository,
    private val connectivity: ConnectivityObserver,
) {

    suspend operator fun invoke(
        instanceId: String,
        parent: Comment,
        text: String,
    ): Outcome<Comment> {
        if (text.isBlank()) {
            return Outcome.Failure(AppError.WordPress("empty_reply", 400))
        }
        if (!connectivity.isCurrentlyOnline()) {
            return Outcome.Failure(AppError.OfflineWriteBlocked)
        }
        return repository.reply(
            instanceId = instanceId,
            postId = parent.postId,
            parentId = parent.id,
            content = text.trim(),
        )
    }
}
