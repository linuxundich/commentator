package de.christophlangner.commentator.domain.model

/**
 * Worüber die Hintergrundprüfung benachrichtigt.
 *
 * Die Werte entsprechen dem `status`-Parameter der WordPress-API. Deren
 * Benennung ist irreführend: `all` bedeutet dort *nicht* alles, sondern nur
 * genehmigt und offen – Spam und Papierkorb sind ausgeschlossen. Wer die
 * wirklich sehen will, braucht `any`.
 */
enum class NotifyScope(val queryValue: String) {

    /** Nur was auf Moderation wartet. Sinnvoll für Blogs, die tatsächlich moderieren. */
    PENDING("hold"),

    /**
     * Jeder neue Kommentar, ohne von einem Spamfilter Aussortiertes.
     *
     * Voreinstellung: Blogs, die automatisch freischalten, hätten mit
     * [PENDING] nie etwas zu melden.
     */
    NEW_COMMENTS("all"),

    /** Zusätzlich Spam und Papierkorb – nützlich, um Fehleinstufungen zu bemerken. */
    EVERYTHING("any"),
    ;

    companion object {
        val DEFAULT = NEW_COMMENTS

        fun fromStorage(value: String?): NotifyScope =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
