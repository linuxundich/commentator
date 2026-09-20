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
) {
    val restBaseUrl: String get() = "$siteUrl/wp-json/"
}
