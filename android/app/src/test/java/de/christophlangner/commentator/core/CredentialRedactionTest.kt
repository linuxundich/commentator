package de.christophlangner.commentator.core

import de.christophlangner.commentator.data.account.ApplicationPassword
import de.christophlangner.commentator.data.account.InstanceCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sicherheitseigenschaft, kein Detail: Zugangsdaten dürfen nicht über
 * [toString] in Logs oder Ausnahmemeldungen gelangen. Diese Tests schlagen
 * fehl, sobald jemand daraus eine data class macht.
 */
class CredentialRedactionTest {

    private val secret = "abcd EFGH 1234 ijkl"

    @Test
    fun `Passwort erscheint nicht in toString`() {
        val password = ApplicationPassword(secret)
        assertFalse(password.toString().contains("abcd"))
        assertEquals("ApplicationPassword(redacted)", password.toString())
    }

    @Test
    fun `Zugangsdaten erscheinen nicht in toString`() {
        val credentials = InstanceCredentials("moderator", ApplicationPassword(secret))
        val text = credentials.toString()
        assertFalse(text.contains("abcd"))
        assertFalse(text.contains("EFGH"))
        assertTrue(text.contains("moderator"))
    }

    @Test
    fun `Passwort ist ueber reveal erreichbar`() {
        assertEquals(secret, ApplicationPassword(secret).reveal())
    }

    @Test
    fun `Basic-Auth-Kopfzeile wird korrekt gebildet`() {
        val credentials = InstanceCredentials("user", ApplicationPassword("pass"))
        assertEquals("Basic dXNlcjpwYXNz", credentials.toBasicAuthHeader())
    }
}
