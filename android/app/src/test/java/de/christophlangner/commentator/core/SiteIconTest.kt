package de.christophlangner.commentator.core

import de.christophlangner.commentator.core.net.SiteIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Das Blog-Symbol aus dem Seitenkopf.
 *
 * Gebraucht für Blogs, die kein WordPress-Site-Icon gesetzt haben und ihr
 * Symbol stattdessen im Theme mitbringen – für die REST-API ist es dann
 * unsichtbar, obwohl es im Browser überall auftaucht.
 */
class SiteIconTest {

    /**
     * Die Verweise, wie sie auf einem echten Blog stehen.
     *
     * Abgeschrieben von linuxundich.de – dort steht `.ico` zuerst, gefolgt
     * von SVG, PNG und apple-touch-icon.
     */
    private val echterKopf = """
        <head>
        <link rel="icon" href="https://blog.test/wp-content/themes/t/favicon.ico" sizes="any"/>
        <link rel="icon" type="image/svg+xml" href="https://blog.test/wp-content/themes/t/favicon.svg"/>
        <link rel="icon" type="image/png" sizes="96x96" href="https://blog.test/wp-content/themes/t/favicon-96x96.png"/>
        <link rel="apple-touch-icon" sizes="180x180" href="https://blog.test/wp-content/themes/t/apple-touch-icon.png"/>
        </head>
    """.trimIndent()

    @Test
    fun `nimmt das groesste zeichenbare Symbol`() {
        assertEquals(
            "https://blog.test/wp-content/themes/t/apple-touch-icon.png",
            SiteIcon.fromHtml(echterKopf, "https://blog.test"),
        )
    }

    @Test
    fun `ico und svg kommen nicht in Frage`() {
        // Androids Bilddekoder kann kein ICO, und fuer SVG fehlt die
        // Coil-Erweiterung. Ein Verweis darauf ergaebe eine leere Flaeche.
        val kopf = """
            <link rel="icon" href="https://blog.test/favicon.ico" sizes="any">
            <link rel="icon" type="image/svg+xml" href="https://blog.test/favicon.svg">
        """.trimIndent()

        assertNull(SiteIcon.fromHtml(kopf, "https://blog.test"))
    }

    @Test
    fun `ein Symbol auf fremdem Rechner wird nicht genommen`() {
        // Die App sagt zu, sich nur mit dem eingerichteten Blog zu verbinden.
        val kopf = """<link rel="icon" sizes="192x192" href="https://cdn.example.net/icon.png">"""

        assertNull(SiteIcon.fromHtml(kopf, "https://blog.test"))
    }

    @Test
    fun `unverschluesselt wird nicht geladen`() {
        val kopf = """<link rel="icon" sizes="192x192" href="http://blog.test/icon.png">"""

        assertNull(SiteIcon.fromHtml(kopf, "https://blog.test"))
    }

    @Test
    fun `ein relativer Verweis wird aufgeloest`() {
        val kopf = """<link rel="icon" sizes="128x128" href="/assets/icon.png">"""

        assertEquals(
            "https://blog.test/assets/icon.png",
            SiteIcon.fromHtml(kopf, "https://blog.test/"),
        )
    }

    @Test
    fun `apple-touch-icon ohne Groessenangabe gilt als gross`() {
        // Ohne Angabe waere es sonst schlechter als die kleine Browserfahne,
        // dabei ist es ueblicherweise 180 x 180.
        val kopf = """
            <link rel="icon" sizes="32x32" href="https://blog.test/klein.png">
            <link rel="apple-touch-icon" href="https://blog.test/gross.png">
        """.trimIndent()

        assertEquals("https://blog.test/gross.png", SiteIcon.fromHtml(kopf, "https://blog.test"))
    }

    @Test
    fun `andere Verweise im Kopf stoeren nicht`() {
        val kopf = """
            <link rel="stylesheet" href="https://blog.test/style.css">
            <link rel="preload" as="image" href="https://blog.test/held.png">
            <link rel="icon" sizes="64x64" href="https://blog.test/icon.png">
        """.trimIndent()

        assertEquals("https://blog.test/icon.png", SiteIcon.fromHtml(kopf, "https://blog.test"))
    }

    @Test
    fun `was nach dem Kopf steht zaehlt nicht`() {
        val html = """
            <head><link rel="icon" sizes="64x64" href="https://blog.test/echt.png"></head>
            <body><link rel="icon" sizes="512x512" href="https://blog.test/untergeschoben.png"></body>
        """.trimIndent()

        assertEquals("https://blog.test/echt.png", SiteIcon.fromHtml(html, "https://blog.test"))
    }

    @Test
    fun `ohne Verweis gibt es nichts`() {
        assertNull(SiteIcon.fromHtml("<head><title>Blog</title></head>", "https://blog.test"))
    }
}
