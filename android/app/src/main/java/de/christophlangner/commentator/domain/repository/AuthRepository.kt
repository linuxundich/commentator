package de.christophlangner.commentator.domain.repository

import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.domain.model.WordPressInstance
import kotlinx.coroutines.flow.Flow

/** Ergebnis der Prüfung einer Blog-Adresse. */
data class SiteDiscovery(
    val siteUrl: String,
    val siteName: String,
    /** Endpunkt für den Autorisierungs-Flow, sofern die Installation ihn anbietet. */
    val authorizationEndpoint: String?,
)

/**
 * Einrichtung und Sitzungszustand.
 *
 * Alle Methoden sind instanzbezogen formuliert, auch wenn Version 1 nur eine
 * Instanz zulässt.
 */
interface AuthRepository {

    /** Die derzeit eingerichtete Instanz, oder `null` vor der Einrichtung. */
    fun observeActiveInstance(): Flow<WordPressInstance?>

    /**
     * Ob die gespeicherten Zugangsdaten zuletzt abgelehnt wurden. Ist das der
     * Fall, sperrt die Oberfläche schreibende Aktionen und bietet eine
     * erneute Anmeldung an.
     */
    fun observeSessionInvalid(): Flow<Boolean>

    /** Prüft, ob unter der Adresse eine WordPress-REST-API erreichbar ist. */
    suspend fun discoverSite(rawUrl: String): Outcome<SiteDiscovery>

    /** Baut die URL des Autorisierungs-Flows inklusive Rücksprungadresse. */
    fun authorizationUrl(discovery: SiteDiscovery): String?

    /**
     * Schließt die Anmeldung ab: prüft die Zugangsdaten gegen
     * `users/me`, legt die Instanz an und speichert das Application Password
     * verschlüsselt.
     */
    suspend fun completeSignIn(
        siteUrl: String,
        username: String,
        applicationPassword: String,
    ): Outcome<WordPressInstance>

    /**
     * Bewertet neu, was der Blog kann: Bridge-Plugin, Moderationsrecht und
     * Name der Installation.
     *
     * Diese Angaben stammen sonst aus dem Moment der Anmeldung. Wird das
     * Plugin später installiert oder die Rolle geändert, bliebe die App
     * ansonsten dauerhaft beim alten Stand.
     */
    suspend fun refreshSiteCapabilities(): Outcome<WordPressInstance>

    /** Meldet ab und löscht Zugangsdaten, Schlüsselmaterial und Cache. */
    suspend fun signOut()
}
