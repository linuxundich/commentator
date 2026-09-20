package de.christophlangner.commentator.core.error

/**
 * Geschlossene Fehlerhierarchie der Anwendung.
 *
 * Alles, was aus Netzwerk, Datenbank oder Authentifizierung kommt, wird in
 * einen dieser Fälle übersetzt, bevor es die Datenschicht verlässt. Die UI
 * kennt deshalb weder HTTP-Codes noch Exceptions.
 */
sealed interface AppError {

    /** Kein Netz, DNS-Fehler oder Verbindungsabbruch. */
    data object NoConnection : AppError

    /** Der Server hat nicht rechtzeitig geantwortet. */
    data object Timeout : AppError

    /** TLS-Handshake oder Zertifikatsprüfung fehlgeschlagen. */
    data object InsecureConnection : AppError

    /** Zugangsdaten fehlen, sind abgelaufen oder wurden widerrufen (HTTP 401). */
    data object Unauthorized : AppError

    /** Angemeldet, aber ohne ausreichende Berechtigung (HTTP 403). */
    data object Forbidden : AppError

    /** Die angefragte Ressource existiert nicht (HTTP 404). */
    data object NotFound : AppError

    /** Zu viele Anfragen (HTTP 429). */
    data class RateLimited(val retryAfterSeconds: Long?) : AppError

    /** Serverseitiger Fehler (HTTP 5xx). */
    data class ServerError(val code: Int) : AppError

    /** Unter der angegebenen Adresse ist keine WordPress-REST-API erreichbar. */
    data object InvalidSite : AppError

    /** Die Installation bietet keine Application Passwords an (meist fehlendes HTTPS). */
    data object ApplicationPasswordsUnavailable : AppError

    /** Die Site-Adresse ist nicht über HTTPS erreichbar bzw. nutzt Klartext. */
    data object InsecureSiteUrl : AppError

    /** Die Antwort war syntaktisch nicht verwertbar. */
    data object MalformedResponse : AppError

    /** Eine schreibende Aktion wurde ohne Verbindung versucht. */
    data object OfflineWriteBlocked : AppError

    /**
     * Ein von WordPress gemeldeter, fachlicher Fehler.
     * [code] ist der WordPress-Fehlercode, etwa `rest_comment_invalid_id`.
     */
    data class WordPress(val code: String, val status: Int) : AppError

    /** Alles Übrige. */
    data class Unknown(val marker: String) : AppError
}
