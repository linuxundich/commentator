package de.christophlangner.commentator.core

import de.christophlangner.commentator.core.net.SiteUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Hier fällt eine Sicherheitsentscheidung: Was nicht über HTTPS läuft, wird
 * gar nicht erst zu einer Adresse.
 */
class SiteUrlTest {

    @Test
    fun `fehlendes Schema wird zu https ergaenzt`() {
        assertEquals("https://example.com", SiteUrl.normalize("example.com"))
    }

    @Test
    fun `http wird abgelehnt`() {
        assertNull(SiteUrl.normalize("http://example.com"))
        assertNull(SiteUrl.normalize("HTTP://example.com"))
    }

    @Test
    fun `abschliessende Schraegstriche und Leerzeichen verschwinden`() {
        assertEquals("https://example.com", SiteUrl.normalize("  https://example.com/  "))
        assertEquals("https://example.com", SiteUrl.normalize("https://example.com///"))
    }

    @Test
    fun `Unterverzeichnis bleibt erhalten`() {
        assertEquals("https://example.com/blog", SiteUrl.normalize("https://example.com/blog/"))
    }

    @Test
    fun `abweichender Port bleibt erhalten`() {
        assertEquals("https://localhost:8443", SiteUrl.normalize("https://localhost:8443"))
    }

    @Test
    fun `Standardport wird nicht angehaengt`() {
        assertEquals("https://example.com", SiteUrl.normalize("https://example.com:443"))
    }

    @Test
    fun `leere und unsinnige Eingaben ergeben null`() {
        assertNull(SiteUrl.normalize(""))
        assertNull(SiteUrl.normalize("   "))
        assertNull(SiteUrl.normalize("ftp://example.com"))
        assertNull(SiteUrl.normalize("::::"))
    }
}
