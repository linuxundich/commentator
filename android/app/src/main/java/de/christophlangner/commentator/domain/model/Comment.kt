package de.christophlangner.commentator.domain.model

import java.time.Instant

/**
 * Ein Kommentar, wie ihn die Anwendung versteht.
 *
 * Bewusst getrennt vom Transportobjekt der API: Ändert WordPress ein Feld,
 * ist nur das Mapping betroffen, nicht die Oberfläche.
 */
data class Comment(
    val id: Long,
    val instanceId: String,
    val postId: Long,
    val parentId: Long,
    /** Nutzer-ID des Verfassers; 0 bedeutet Gast. */
    val authorId: Long,
    val authorName: String,
    val authorEmail: String?,
    val authorUrl: String?,
    val avatarUrl: String?,
    /** Gerendertes HTML, wie es WordPress liefert. */
    val contentHtml: String,
    /** Reiner Text für Listenvorschau, Suche und Benachrichtigungen. */
    val contentPlain: String,
    val date: Instant,
    val status: CommentStatus,
    val postTitle: String?,
    val link: String?,
) {
    val isReply: Boolean get() = parentId != 0L
}
