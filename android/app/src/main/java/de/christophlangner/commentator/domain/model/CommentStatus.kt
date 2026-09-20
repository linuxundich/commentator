package de.christophlangner.commentator.domain.model

/**
 * Kommentarstatus von WordPress.
 *
 * WordPress verwendet für dieselben Zustände zwei Schreibweisen: In der
 * Abfrage heißt „genehmigt“ `approve`, im zurückgelieferten Objekt dagegen
 * `approved`. Beides wird hier an einer Stelle abgebildet, damit sich die
 * Unterscheidung nicht durch den Code zieht.
 */
enum class CommentStatus(
    /** Wert für den `status`-Parameter beim Auflisten. */
    val queryValue: String,
    /** Wert, den WordPress beim Schreiben erwartet. */
    val writeValue: String,
) {
    APPROVED("approve", "approved"),
    PENDING("hold", "hold"),
    SPAM("spam", "spam"),
    TRASH("trash", "trash"),
    ;

    companion object {
        /**
         * Liest den Status aus einer API-Antwort. Unbekannte Werte gelten als
         * ausstehend – in der Moderation ist es der sicherere Standard, einen
         * Kommentar zu viel zu zeigen als einen zu wenig.
         */
        fun fromApi(value: String?): CommentStatus = when (value?.lowercase()) {
            "approved", "approve", "1" -> APPROVED
            "hold", "unapproved", "0" -> PENDING
            "spam" -> SPAM
            "trash" -> TRASH
            else -> PENDING
        }
    }
}
