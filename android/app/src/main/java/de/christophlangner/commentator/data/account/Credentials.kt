package de.christophlangner.commentator.data.account

import okhttp3.Credentials

/**
 * Ein Application Password.
 *
 * Eigener Typ, damit der Wert nicht versehentlich in Logs, Fehlermeldungen
 * oder Absturzberichten landet: [toString] gibt ihn nicht preis, und der Wert
 * ist nur über [reveal] erreichbar.
 */
class ApplicationPassword(private val value: String) {

    fun reveal(): String = value

    val isBlank: Boolean get() = value.isBlank()

    override fun toString(): String = "ApplicationPassword(redacted)"

    override fun equals(other: Any?): Boolean =
        other is ApplicationPassword && other.value == value

    override fun hashCode(): Int = value.hashCode()
}

/** Zugangsdaten einer Instanz, wie sie für Basic Auth gebraucht werden. */
data class InstanceCredentials(
    val username: String,
    val password: ApplicationPassword,
) {
    fun toBasicAuthHeader(): String = Credentials.basic(username, password.reveal())

    override fun toString(): String = "InstanceCredentials(username=$username, password=redacted)"
}
