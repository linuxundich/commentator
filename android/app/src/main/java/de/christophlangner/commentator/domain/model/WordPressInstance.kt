package de.christophlangner.commentator.domain.model

/**
 * Eine konfigurierte WordPress-Installation.
 *
 * Version 1 verwaltet genau eine Instanz, die Datenhaltung ist aber schon
 * jetzt mehrinstanzenfähig: [id] ist eine lokal vergebene, stabile Kennung
 * und nicht die Blog-Adresse, damit ein Umzug der Website die lokalen Daten
 * nicht entwertet.
 */
data class WordPressInstance(
    val id: String,
    val displayName: String,
    /** Basisadresse der Website, immer mit https und ohne abschließenden Schrägstrich. */
    val siteUrl: String,
    val username: String,
    val userId: Long,
    /** Ob das Konto `moderate_comments` besitzt. */
    val canModerate: Boolean,
    /** Ob das Plugin `commentator-bridge` erkannt wurde. */
    val hasBridgePlugin: Boolean,
    /** Ob das Konto seitenweite Optionen aendern darf, etwa die Sperrliste. */
    val canManageOptions: Boolean = false,
    /** Das in WordPress gesetzte Site-Icon, sofern eines hinterlegt ist. */
    val iconUrl: String? = null,
    /**
     * Das Symbol aus dem Seitenkopf, für Blogs ohne gesetztes Site-Icon.
     *
     * Bewusst ein eigenes Feld: Würde es in [iconUrl] landen, ließe sich
     * später nicht mehr unterscheiden, ob dort ein entferntes Site-Icon
     * nachhallt oder ein Fund aus dem Theme steht – und ein im Blog
     * gelöschtes Site-Icon bliebe für immer stehen.
     */
    val themeIconUrl: String? = null,
    /** Der Name, den WordPress meldet; [displayName] fällt darauf zurück. */
    val siteName: String = displayName,
    /** Vom Nutzer vergebener Name; hat Vorrang vor [siteName] und überlebt das Auffrischen. */
    val customName: String? = null,
) {
    val restBaseUrl: String get() = "$siteUrl/wp-json/"

    /**
     * Das Symbol, das angezeigt wird.
     *
     * Das gesetzte Site-Icon hat Vorrang; es ist die ausdrückliche Wahl des
     * Blogbetreibers. Der Fund aus dem Seitenkopf springt nur ein.
     */
    val displayIconUrl: String? get() = iconUrl ?: themeIconUrl
}
