package de.christophlangner.commentator.domain.model

/**
 * Eine Moderationsentscheidung.
 *
 * [Delete] unterscheidet zwischen Papierkorb und endgültigem Löschen, weil
 * WordPress beides über denselben Endpunkt abwickelt und der Unterschied für
 * den Benutzer erheblich ist.
 */
sealed interface ModerationAction {
    data object Approve : ModerationAction
    data object Hold : ModerationAction
    data object MarkAsSpam : ModerationAction
    data class Delete(val permanent: Boolean) : ModerationAction

    /** Der Status, den der Kommentar nach der Aktion hat – für optimistische Anzeige. */
    val resultingStatus: CommentStatus?
        get() = when (this) {
            Approve -> CommentStatus.APPROVED
            Hold -> CommentStatus.PENDING
            MarkAsSpam -> CommentStatus.SPAM
            is Delete -> if (permanent) null else CommentStatus.TRASH
        }
}
