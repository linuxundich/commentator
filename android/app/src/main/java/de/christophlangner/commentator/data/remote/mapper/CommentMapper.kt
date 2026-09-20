package de.christophlangner.commentator.data.remote.mapper

import de.christophlangner.commentator.core.text.htmlToPlainText
import de.christophlangner.commentator.data.local.entity.CommentEntity
import de.christophlangner.commentator.data.local.entity.CommentWithPost
import de.christophlangner.commentator.data.remote.dto.CommentDto
import de.christophlangner.commentator.domain.model.Comment
import de.christophlangner.commentator.domain.model.CommentStatus
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * Übersetzt zwischen API, Datenbank und Domäne.
 *
 * Die einzige Stelle, an der die Feldnamen von WordPress vorkommen. Ändert
 * sich dort etwas, bleibt es hier.
 */
object CommentMapper {

    /**
     * WordPress liefert `date_gmt` ohne Zeitzonenangabe, gemeint ist aber
     * immer UTC. Fehlt das Feld, wird auf die lokale Variante zurückgegriffen.
     */
    fun parseGmtDate(dateGmt: String?, fallback: String? = null): Instant {
        val candidate = dateGmt ?: fallback ?: return Instant.EPOCH
        return try {
            LocalDateTime.parse(candidate).toInstant(ZoneOffset.UTC)
        } catch (error: DateTimeParseException) {
            runCatching { Instant.parse(candidate) }.getOrDefault(Instant.EPOCH)
        }
    }

    fun toEntity(dto: CommentDto, instanceId: String): CommentEntity = CommentEntity(
        instanceId = instanceId,
        id = dto.id,
        postId = dto.post,
        parentId = dto.parent,
        authorName = dto.authorName.ifBlank { "" },
        authorEmail = dto.authorEmail?.takeIf { it.isNotBlank() },
        authorUrl = dto.authorUrl?.takeIf { it.isNotBlank() },
        avatarUrl = dto.bestAvatarUrl(),
        contentHtml = dto.content.rendered,
        contentPlain = dto.content.rendered.htmlToPlainText(),
        dateEpochMillis = parseGmtDate(dto.dateGmt, dto.date).toEpochMilli(),
        status = CommentStatus.fromApi(dto.status).name,
        link = dto.link,
    )

    fun toDomain(entity: CommentEntity, postTitle: String?): Comment = Comment(
        id = entity.id,
        instanceId = entity.instanceId,
        postId = entity.postId,
        parentId = entity.parentId,
        authorName = entity.authorName,
        authorEmail = entity.authorEmail,
        authorUrl = entity.authorUrl,
        avatarUrl = entity.avatarUrl,
        contentHtml = entity.contentHtml,
        contentPlain = entity.contentPlain,
        date = Instant.ofEpochMilli(entity.dateEpochMillis),
        status = runCatching { CommentStatus.valueOf(entity.status) }
            .getOrDefault(CommentStatus.PENDING),
        postTitle = postTitle,
        link = entity.link,
    )

    fun toDomain(row: CommentWithPost): Comment = toDomain(row.comment, row.postTitle)

    fun toDomain(dto: CommentDto, instanceId: String, postTitle: String? = null): Comment =
        toDomain(toEntity(dto, instanceId), postTitle)
}
