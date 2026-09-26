package de.christophlangner.commentator.domain.model

/**
 * Farbton, mit dem die Beitraege einer Rolle ausgezeichnet werden.
 *
 * Bewusst eine feste Auswahl statt eines freien Farbwaehlers: Jeder Ton ist
 * fuer beide Erscheinungsbilder abgestimmt und traegt genug Kontrast fuer den
 * Text darauf. Eine frei gewaehlte Farbe koennte das nicht zusichern - und
 * eine Liste, in der einzelne Karten unlesbar werden, waere schlechter als
 * gar keine Farben.
 *
 * Die Toene selbst stehen in `ui/theme/RoleColors.kt`; hier steht nur, welche
 * es gibt.
 */
enum class RoleAccent {
    SCHIEFER,
    INDIGO,
    TEAL,
    MOOS,
    OCKER,
    TERRAKOTTA,
    PFLAUME,
    ;

    companion object {
        val DEFAULT = SCHIEFER

        fun fromStorage(value: String?): RoleAccent =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

/**
 * Wie eine Rolle in der App behandelt wird.
 *
 * Gilt je Rollenkuerzel und nicht je Person: Wer einen Blog moderiert, denkt
 * in Rollen. Waere es je Konto einstellbar, muesste jede neue Autorin einzeln
 * nachgetragen werden.
 */
data class RoleStyle(
    val accent: RoleAccent = RoleAccent.DEFAULT,
    /**
     * Ob die Farbe ueberhaupt verwendet wird.
     *
     * Aus bedeutet nicht "unsichtbar": Die Marke mit dem Rollennamen bleibt,
     * nur der Grundton der Karte entfaellt. Wer eine ruhige Liste will, soll
     * sie bekommen, ohne die Rolle aus den Augen zu verlieren.
     */
    val colorEnabled: Boolean = true,
    /**
     * Ob die Kommentare dieser Rolle einzeln in der Liste stehen.
     *
     * Aus heisst eingeklappt, nicht ausgeblendet: An ihrer Stelle steht eine
     * Zeile, die sagt, wie viele Beitraege des Teams dort stehen. Ganz zu
     * verschwinden waere schlechter als jede Filterung - man wuesste nicht
     * einmal, dass etwas fehlt.
     */
    val showInTimeline: Boolean = true,
    /** Ob neue Kommentare dieser Rolle eine Benachrichtigung ausloesen. */
    val notify: Boolean = true,
)

/**
 * Die Einstellungen aller Rollen.
 *
 * Nicht eingetragene Rollen bekommen die Voreinstellung - die Liste der
 * Rollen haengt am Blog und kann sich jederzeit aendern.
 */
data class RoleStyles(val byRole: Map<String, RoleStyle> = emptyMap()) {

    fun of(slug: String): RoleStyle = byRole[slug] ?: defaultFor(slug)

    fun with(slug: String, style: RoleStyle): RoleStyles =
        RoleStyles(byRole + (slug to style))

    /** Rollen, deren Kommentare in der Liste zusammengefasst werden. */
    val collapsedRoles: Set<String>
        get() = byRole.filterValues { !it.showInTimeline }.keys

    /** Ob ueber Kommentare dieser Rolle benachrichtigt wird. */
    fun notifies(slug: String): Boolean = of(slug).notify

    companion object {
        val DEFAULT = RoleStyles()

        /**
         * Voreingestellter Farbton je Rolle.
         *
         * Von der Leitung nach aussen: Indigo fuer die Administration, dann
         * Teal, Moos, Ocker. Alle vier sind gedaempft - ein Beitrag des Teams
         * ist eine Einordnung, keine Warnung.
         */
        fun defaultFor(slug: String): RoleStyle = RoleStyle(
            accent = when (slug) {
                Team.ADMINISTRATOR -> RoleAccent.INDIGO
                Team.EDITOR -> RoleAccent.TEAL
                Team.AUTHOR -> RoleAccent.MOOS
                Team.CONTRIBUTOR -> RoleAccent.OCKER
                else -> RoleAccent.SCHIEFER
            },
        )
    }
}
