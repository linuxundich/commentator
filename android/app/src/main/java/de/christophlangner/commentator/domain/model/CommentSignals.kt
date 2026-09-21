package de.christophlangner.commentator.domain.model

/**
 * Auffälligkeiten an einem Kommentar.
 *
 * Bewusst nur Hinweise, kein Urteil: Die App stuft nichts selbsttätig als
 * Spam ein. Sie legt offen, was auffällt, und überlässt die Entscheidung dem
 * Menschen - eine falsch automatisierte Einstufung wäre schlimmer als ein
 * Kommentar, den jemand selbst ansieht.
 */
data class CommentSignals(
    /** Anzahl der Links im Kommentartext. Das aussagekräftigste Einzelmerkmal. */
    val linkCount: Int = 0,
    /**
     * Ob derselbe Text noch einmal unter den geladenen Kommentaren vorkommt.
     * Nur innerhalb des Geladenen bestimmbar, nie über den ganzen Blog.
     */
    val duplicated: Boolean = false,
    /**
     * Ob von dieser Adresse bisher kein Kommentar freigeschaltet wurde.
     * `null`, solange es nicht ermittelt wurde - dann wird nichts angezeigt.
     */
    val firstTimeAuthor: Boolean? = null,
) {
    val hasAny: Boolean get() = linkCount > 0 || duplicated || firstTimeAuthor == true

    companion object {
        /**
         * Zählt Verweise im gerenderten HTML.
         *
         * Gezählt wird beides: gesetzte Links und nackt im Text stehende
         * Adressen. Nur das Markup zu betrachten griff zu kurz – WordPress
         * verlinkt nicht jede Adresse automatisch, und ein Spamkommentar mit
         * ausgeschriebener URL wäre unauffällig geblieben.
         *
         * Damit nichts doppelt zählt, werden vollständige Anker zuerst
         * entfernt: Steht die Adresse als Beschriftung im Link, ist sie sonst
         * zweimal drin.
         */
        fun linkCountOf(contentHtml: String): Int {
            val anker = ANCHOR.findAll(contentHtml).count()
            val ohneAnker = ANCHOR_ELEMENT.replace(contentHtml, " ")
            return anker + BARE_URL.findAll(ohneAnker).count()
        }

        /**
         * Markiert Kommentare, deren Text mehrfach vorkommt.
         *
         * Vergleicht den reinen Text, weil sich dasselbe HTML leicht
         * variieren lässt, ohne dass sich am Gelesenen etwas ändert.
         */
        fun duplicatedIds(comments: List<Comment>): Set<Long> =
            comments
                .groupBy { it.contentPlain.lowercase().trim() }
                .filterKeys { it.isNotBlank() }
                .filterValues { it.size > 1 }
                .values
                .flatten()
                .mapTo(mutableSetOf()) { it.id }

        private val ANCHOR = Regex("""<a\s[^>]*href\s*=""", RegexOption.IGNORE_CASE)

        /** Vollstaendiges Ankerelement samt Beschriftung. */
        private val ANCHOR_ELEMENT = Regex(
            """<a\b[^>]*>.*?</a\s*>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )

        /**
         * Adresse, die ohne Verlinkung im Text steht.
         *
         * Bewusst nur mit Schema oder fuehrendem www: Ein blosses
         * "beispiel.de" mitzuzaehlen traefe zu oft Saetze, in denen nur ueber
         * eine Seite gesprochen wird.
         */
        private val BARE_URL = Regex(
            """(https?://|www\.)\S{2,}""",
            RegexOption.IGNORE_CASE,
        )
    }
}
