package de.christophlangner.commentator.domain.model

/** Auswahl der Filterleiste im Posteingang. */
enum class CommentFilter(val status: CommentStatus?) {
    ALL(null),
    PENDING(CommentStatus.PENDING),

    /**
     * Freigegebene Kommentare von Lesern, unter denen noch keine Antwort aus
     * dem Team steht.
     *
     * WordPress kennt diesen Zustand nicht; er wird aus dem Zwischenspeicher
     * berechnet. Abgerufen wird deshalb wie bei [APPROVED], ergänzt um die
     * Antworten auf die geladenen Kommentare. Eine Zahl vom Server gibt es
     * dafür nicht.
     */
    UNANSWERED(CommentStatus.APPROVED),
    APPROVED(CommentStatus.APPROVED),
    SPAM(CommentStatus.SPAM),
    TRASH(CommentStatus.TRASH),
    ;

    /**
     * Wert für den `status`-Parameter der API. WordPress kennt für „alles“
     * den Sonderwert `all`.
     */
    val queryValue: String get() = status?.queryValue ?: "all"

    /** Ob der Server diesen Filter zählen kann. */
    val countedOnServer: Boolean get() = this != UNANSWERED
}
