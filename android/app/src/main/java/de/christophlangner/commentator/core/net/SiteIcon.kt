package de.christophlangner.commentator.core.net

import java.net.URI

/**
 * Das Symbol eines Blogs, das nicht als WordPress-Site-Icon hinterlegt ist.
 *
 * Die REST-API gibt unter `site_icon_url` nur das aus, was in WordPress
 * ausdrücklich als Site Icon gesetzt wurde. Viele Blogs bringen ihr Symbol
 * stattdessen im Theme mit und tragen es nur als `<link rel="icon">` in den
 * Seitenkopf ein – für die API ist es dann unsichtbar, obwohl es im Browser
 * überall auftaucht.
 *
 * Deshalb der zweite Blick in den Seitenkopf. Bewusst mit engen Grenzen:
 */
object SiteIcon {

    /**
     * Sucht im Seitenkopf nach einem verwendbaren Symbol.
     *
     * Genommen wird das größte angegebene Bild, das
     * - auf **demselben Rechner** liegt wie der Blog. Ein Symbol von einem
     *   Auslieferungsnetz wäre eine Verbindung zu einem Dritten, und die
     *   sagt die App ausdrücklich nicht zu.
     * - über HTTPS erreichbar ist, wie alles andere auch.
     * - in einem Format vorliegt, das Android zeichnen kann. `.ico` gehört
     *   nicht dazu, `.svg` mangels Coil-Erweiterung auch nicht – ein Verweis
     *   darauf ergäbe eine leere Fläche statt eines Symbols.
     *
     * @param html Der Seitenkopf; der Rest der Seite wird nicht gebraucht.
     * @param siteUrl Die Adresse des Blogs, für relative Verweise und den
     *   Abgleich des Rechnernamens.
     */
    fun fromHtml(html: String, siteUrl: String): String? {
        val blog = runCatching { URI(siteUrl) }.getOrNull() ?: return null
        val kopf = html.substringBefore("</head>", html)

        return LINK.findAll(kopf)
            .mapNotNull { treffer -> kandidat(treffer.value, blog) }
            .maxByOrNull { it.groesse }
            ?.url
    }

    private data class Kandidat(val url: String, val groesse: Int)

    private fun kandidat(tag: String, blog: URI): Kandidat? {
        val rel = attribut(tag, "rel")?.lowercase() ?: return null
        if (!rel.split(" ").any { it == "icon" || it == "apple-touch-icon" }) return null

        val href = attribut(tag, "href") ?: return null
        val absolut = runCatching { blog.resolve(href.trim()) }.getOrNull() ?: return null

        if (!absolut.scheme.equals("https", ignoreCase = true)) return null
        if (!absolut.host.equals(blog.host, ignoreCase = true)) return null
        if (!zeichenbar(absolut.path.orEmpty())) return null

        return Kandidat(absolut.toString(), groesse(tag, rel))
    }

    /**
     * Wie groß das Symbol laut Angabe ist.
     *
     * `sizes="96x96"` ist eindeutig. Ein `apple-touch-icon` ohne Angabe ist
     * erfahrungsgemäß 180 × 180 und damit besser als ein Verweis ganz ohne
     * Angabe, der meist auf die kleine Browserfahne zeigt.
     */
    private fun groesse(tag: String, rel: String): Int {
        attribut(tag, "sizes")?.lowercase()?.let { angabe ->
            GROESSE.find(angabe)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        }
        return if (rel.contains("apple-touch-icon")) APPLE_TOUCH else 0
    }

    private fun zeichenbar(pfad: String): Boolean =
        ZEICHENBAR.any { pfad.endsWith(it, ignoreCase = true) }

    private fun attribut(tag: String, name: String): String? =
        Regex("""$name\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
            .find(tag)
            ?.groupValues
            ?.get(1)

    private val LINK = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE)

    private val GROESSE = Regex("""(\d+)\s*x\s*\d+""")

    /** Formate, die Androids Bilddekoder beherrscht. */
    private val ZEICHENBAR = listOf(".png", ".jpg", ".jpeg", ".webp")

    /** Übliche Kantenlänge eines `apple-touch-icon` ohne `sizes`. */
    private const val APPLE_TOUCH = 180
}
