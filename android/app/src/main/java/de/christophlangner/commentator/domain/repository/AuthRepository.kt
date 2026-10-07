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
 * Alle blogbezogenen Methoden nehmen die Kennung ausdruecklich. Es gibt keine
 * Methode, die stillschweigend auf den aktiven Blog wirkt: Die
 * Hintergrundpruefung arbeitet auf allen, und in den Einstellungen wird ein
 * Blog bearbeitet, der gerade nicht der aktive ist.
 */
interface AuthRepository {

    /** Alle eingerichteten Blogs, in der Reihenfolge ihrer Einrichtung. */
    fun observeInstances(): Flow<List<WordPressInstance>>

    /** Der Blog, den der Posteingang gerade zeigt, oder `null` vor der Einrichtung. */
    fun observeActiveInstance(): Flow<WordPressInstance?>

    /** Wechselt den angezeigten Blog. Unbekannte Kennungen bleiben ohne Wirkung. */
    suspend fun setActiveInstance(instanceId: String)

    /**
     * Ob die gespeicherten Zugangsdaten des aktiven Blogs zuletzt abgelehnt
     * wurden. Ist das der Fall, sperrt die Oberfläche schreibende Aktionen und
     * bietet eine erneute Anmeldung an.
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
     *
     * Ist die Adresse bereits eingerichtet, wird der bestehende Eintrag
     * aktualisiert und kein zweiter angelegt - siehe die Begruendung in der
     * Umsetzung.
     */
    suspend fun completeSignIn(
        siteUrl: String,
        username: String,
        applicationPassword: String,
    ): Outcome<WordPressInstance>

    /**
     * Bewertet neu, was ein Blog kann: Bridge-Plugin, Moderationsrecht und
     * Name der Installation.
     *
     * Diese Angaben stammen sonst aus dem Moment der Anmeldung. Wird das
     * Plugin später installiert oder die Rolle geändert, bliebe die App
     * ansonsten dauerhaft beim alten Stand.
     */
    suspend fun refreshSiteCapabilities(instanceId: String): Outcome<WordPressInstance>

    /**
     * Vergibt einen eigenen Namen für den Blog; leer oder null stellt den
     * Namen wieder her, den WordPress meldet.
     */
    suspend fun renameInstance(instanceId: String, name: String?)

    /**
     * Entfernt einen Blog: Zugangsdaten, Zwischenspeicher und Meldestand.
     *
     * Das Schlüsselmaterial im Keystore verschwindet erst mit dem letzten
     * Blog - es wird von allen geteilt.
     */
    suspend fun signOut(instanceId: String)
}
