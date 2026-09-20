package de.christophlangner.commentator.domain.model

/** Auswahl der Filterleiste im Posteingang. */
enum class CommentFilter(val status: CommentStatus?) {
    ALL(null),
    PENDING(CommentStatus.PENDING),
    APPROVED(CommentStatus.APPROVED),
    SPAM(CommentStatus.SPAM),
    TRASH(CommentStatus.TRASH),
    ;

    /**
     * Wert für den `status`-Parameter der API. WordPress kennt für „alles“
     * den Sonderwert `all`.
     */
    val queryValue: String get() = status?.queryValue ?: "all"
}
