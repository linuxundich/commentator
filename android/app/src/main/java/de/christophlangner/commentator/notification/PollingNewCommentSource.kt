package de.christophlangner.commentator.notification

import de.christophlangner.commentator.core.AppLog
import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.data.local.dao.CommentDao
import de.christophlangner.commentator.data.local.entity.NotifiedCommentEntity
import de.christophlangner.commentator.data.local.entity.SyncStateEntity
import de.christophlangner.commentator.data.remote.ApiExecutor
import de.christophlangner.commentator.data.remote.WordPressApiProvider
import de.christophlangner.commentator.data.remote.mapper.CommentMapper
import de.christophlangner.commentator.domain.model.Comment
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.model.WordPressInstance
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fragt regelmäßig ab, ob es neue moderationsbedürftige Kommentare gibt.
 *
 * Ist das Plugin `commentator-bridge` vorhanden, geht der erste Schritt über
 * dessen Statusendpunkt: eine einzelne, indizierte Abfrage, die nur Anzahl,
 * neueste Kennung und deren Zeitstempel liefert. Erst wenn sich daran etwas
 * geändert hat, werden überhaupt Kommentare geladen. Ohne Plugin wird
 * derselbe Zweck mit einer sparsam parametrisierten Kernabfrage erreicht.
 */
@Singleton
class PollingNewCommentSource @Inject constructor(
    private val clientFactory: WordPressApiProvider,
    private val executor: ApiExecutor,
    private val dao: CommentDao,
) : NewCommentSource {

    override suspend fun fetchUnnotified(instance: WordPressInstance): Outcome<List<Comment>> {
        val api = clientFactory.forInstance(instance.id, instance.siteUrl)
        val state = dao.syncState(instance.id)
        val lastNotifiedId = state?.lastNotifiedCommentId ?: 0L
        val lastNotifiedAt = state?.lastNotifiedDateEpochMillis ?: 0L

        if (instance.hasBridgePlugin) {
            val status = executor.call { api.bridgeStatus() }
            when (status) {
                is Outcome.Success -> {
                    val latest = status.value.body.latestCommentId
                    if (latest != 0L && latest <= lastNotifiedId) {
                        return Outcome.Success(emptyList())
                    }
                }
                // Das Plugin ist optional. Fällt es aus, wird einfach die
                // Kernabfrage verwendet statt die Prüfung abzubrechen.
                is Outcome.Failure -> AppLog.d("Bridge-Status nicht verfügbar, nutze Kern-API")
            }
        }

        val after = lastNotifiedAt
            .takeIf { it > 0 }
            ?.let { ISO_UTC.format(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC)) }

        val result = executor.call {
            api.listComments(
                status = CommentStatus.PENDING.queryValue,
                page = 1,
                perPage = MAX_PER_RUN,
                after = after,
            )
        }

        return when (result) {
            is Outcome.Failure -> result
            is Outcome.Success -> {
                val entities = result.value.body
                    .map { CommentMapper.toEntity(it, instance.id) }
                    .filter { it.id > lastNotifiedId }

                if (entities.isEmpty()) return Outcome.Success(emptyList())

                // Der Cache wird mitgefüllt, damit die App nach dem Antippen
                // der Benachrichtigung sofort etwas anzeigen kann.
                dao.upsertComments(entities)

                val suppressed = dao.alreadyNotified(instance.id, entities.map { it.id }).toSet()
                val fresh = entities
                    .filterNot { it.id in suppressed }
                    .sortedBy { it.dateEpochMillis }
                    .map { CommentMapper.toDomain(it, null) }

                Outcome.Success(fresh)
            }
        }
    }

    override suspend fun markNotified(instance: WordPressInstance, comments: List<Comment>) {
        if (comments.isEmpty()) return
        val now = System.currentTimeMillis()

        dao.markNotified(
            comments.map { NotifiedCommentEntity(instance.id, it.id, now) },
        )

        val current = dao.syncState(instance.id)
        val newestId = maxOf(comments.maxOf { it.id }, current?.lastNotifiedCommentId ?: 0L)
        val newestDate = maxOf(
            comments.maxOf { it.date.toEpochMilli() },
            current?.lastNotifiedDateEpochMillis ?: 0L,
        )

        dao.upsertSyncState(
            current?.copy(
                lastNotifiedCommentId = newestId,
                lastNotifiedDateEpochMillis = newestDate,
            ) ?: SyncStateEntity(
                instanceId = instance.id,
                lastSyncEpochMillis = now,
                lastNotifiedCommentId = newestId,
                lastNotifiedDateEpochMillis = newestDate,
            ),
        )

        dao.pruneNotified(now - RETENTION_MILLIS)
    }

    private companion object {
        const val MAX_PER_RUN = 20
        val RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000
        val ISO_UTC: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    }
}
