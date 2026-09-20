package de.christophlangner.commentator.core.text

import androidx.core.text.HtmlCompat

/**
 * WordPress liefert Kommentarinhalte als gerendertes HTML.
 *
 * Für Listenvorschauen wird daraus reiner Text. Die Detailansicht nutzt
 * `AnnotatedString.fromHtml` aus Compose und damit ebenfalls keinen WebView –
 * es wird also weder JavaScript ausgeführt noch werden Remote-Inhalte
 * nachgeladen.
 */
fun String.htmlToPlainText(): String =
    HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_COMPACT)
        .toString()
        .replace(Regex("\\s+"), " ")
        .trim()
