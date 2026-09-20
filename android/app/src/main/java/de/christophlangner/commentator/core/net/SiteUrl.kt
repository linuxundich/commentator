package de.christophlangner.commentator.core.net

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Normalisierung der Blog-Adresse.
 *
 * Eigener Ort statt einer privaten Hilfsfunktion im Repository, weil hier die
 * Sicherheitsentscheidung fällt: Alles, was nicht über HTTPS läuft, wird
 * abgewiesen. Das lässt sich so unabhängig prüfen.
 */
object SiteUrl {

    /**
     * Ergänzt ein fehlendes Schema, entfernt abschließende Schrägstriche und
     * gibt `null` zurück, wenn die Adresse nicht über HTTPS erreichbar wäre.
     */
    fun normalize(raw: String): String? {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isBlank()) return null
        if (trimmed.startsWith("http://", ignoreCase = true)) return null

        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val parsed: HttpUrl = withScheme.toHttpUrlOrNull() ?: return null
        if (!parsed.isHttps) return null

        val path = parsed.encodedPath.trimEnd('/')
        return buildString {
            append(parsed.scheme).append("://").append(parsed.host)
            if (parsed.port != HttpUrl.defaultPort(parsed.scheme)) {
                append(":").append(parsed.port)
            }
            append(path)
        }
    }
}
