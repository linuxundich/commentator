package de.christophlangner.commentator.domain.usecase

import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.core.net.ConnectivityObserver
import de.christophlangner.commentator.domain.model.Comment
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.model.ModerationAction
import de.christophlangner.commentator.domain.repository.CommentAlerts
import de.christophlangner.commentator.domain.repository.CommentRepository
import javax.inject.Inject

/**
 * Veröffentlicht eine Antwort als echten WordPress-Kommentar, der auf den
 * ursprünglichen Kommentar verweist.
 *
 * Wartet der ursprüngliche Kommentar noch auf Freigabe, wird er zuerst
 * freigegeben - wie „Freigeben und antworten" im WordPress-Backend. Ohne das
 * hinge die Antwort unter einem Kommentar, den niemand sieht, und bliebe auf
 * der Website unsichtbar. Erst freigeben, dann antworten: Scheitert die
 * Freigabe, ist nichts geschehen, und ein erneuter Versuch erzeugt keine
 * doppelte Antwort.
 */
class ReplyToCommentUseCase @Inject constructor(
    private val repository: CommentRepository,
    private val connectivity: ConnectivityObserver,
    private val alerts: CommentAlerts = CommentAlerts.NONE,
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
        if (parent.status == CommentStatus.PENDING) {
            val approved = repository.moderate(instanceId, parent.id, ModerationAction.Approve)
            if (approved is Outcome.Failure) return approved
        }
        return repository.reply(
            instanceId = instanceId,
            postId = parent.postId,
            parentId = parent.id,
            content = text.trim(),
        ).also { if (it is Outcome.Success) alerts.dismiss(instanceId, parent.id) }
    }
}
