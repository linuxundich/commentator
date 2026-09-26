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
import de.christophlangner.commentator.domain.model.NotifyScope
import de.christophlangner.commentator.domain.model.Team
import de.christophlangner.commentator.domain.model.WordPressInstance
import de.christophlangner.commentator.domain.repository.SettingsRepository
import de.christophlangner.commentator.domain.repository.TeamRepository
import kotlinx.coroutines.flow.first
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
    private val settingsRepository: SettingsRepository,
    private val teamRepository: TeamRepository,
) : NewCommentSource {

    override suspend fun fetchUnnotified(instance: WordPressInstance): Outcome<NewComments> {
        val api = clientFactory.forInstance(instance.id, instance.siteUrl)
        // Auf Blogs, die automatisch freischalten, gibt es nie etwas mit
        // Status "offen". Dort waere eine Pruefung, die nur darauf sieht,
        // dauerhaft wirkungslos.
        val einstellungen = settingsRepository.siteSettings(instance.id).first()
        val scope = einstellungen.notifyScope
        val state = dao.syncState(instance.id)
        val lastNotifiedId = state?.lastNotifiedCommentId ?: 0L
        val lastNotifiedAt = state?.lastNotifiedDateEpochMillis ?: 0L

        if (instance.hasBridgePlugin) {
            val status = executor.call { api.bridgeStatus() }
            when (status) {
                is Outcome.Success -> {
                    val body = status.value.body
                    // Aeltere Plugin-Fassungen kennen das zweite Feld nicht
                    // und melden 0; dann entfaellt die Abkuerzung und es
                    // wird regulaer abgefragt.
                    val latest = if (scope == NotifyScope.PENDING) {
                        body.latestCommentId
                    } else {
                        body.latestAnyCommentId
                    }
                    if (latest != 0L && latest <= lastNotifiedId) {
                        return Outcome.Success(NewComments.NONE)
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
                status = scope.queryValue,
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

                if (entities.isEmpty()) return Outcome.Success(NewComments.NONE)

                // Der Cache wird mitgefüllt, damit die App nach dem Antippen
                // der Benachrichtigung sofort etwas anzeigen kann.
                dao.upsertComments(entities)

                val suppressed = dao.alreadyNotified(instance.id, entities.map { it.id }).toSet()
                val fresh = entities
                    .filterNot { it.id in suppressed }
                    .sortedBy { it.dateEpochMillis }
                    .map { CommentMapper.toDomain(it, null) }

                // Die Rollen werden erst geholt, wenn es überhaupt etwas zu
                // beurteilen gibt: Ein Lauf ohne neue Kommentare soll keine
                // zusätzliche Anfrage kosten.
                val team = if (fresh.isEmpty()) {
                    Team()
                } else {
                    teamRepository.team(instance.id).valueOrNull ?: Team()
                }

                val (stumm, melden) = fresh.partition { comment ->
                    val kuerzel = meldeKuerzel(comment, instance, team)
                        ?: return@partition false
                    !einstellungen.roleStyles.notifies(kuerzel)
                }

                Outcome.Success(NewComments(toReport = melden, muted = stumm))
            }
        }
    }

    /**
     * Unter welchem Rollenkuerzel ueber einen Kommentar entschieden wird,
     * oder `null` fuer alles, was nicht zum Team gehoert - dessen Kommentare
     * sind die, um die es beim Moderieren ueberhaupt geht.
     *
     * Die eigenen Beitraege zaehlen immer zum eigenen Konto, auch wenn das
     * Plugin die tatsaechliche Rolle kennt. Sonst haette der Schalter beim
     * eigenen Konto ausgerechnet auf den Blogs keine Wirkung, auf denen die
     * App am meisten weiss: Dort faende sich der eigene Beitrag unter
     * "Administrator" wieder, und wer die eigenen Antworten stummschalten
     * will, muesste die ganze Rolle stummschalten - mitsamt den Beitraegen
     * aller anderen Administratoren.
     */
    private fun meldeKuerzel(comment: Comment, instance: WordPressInstance, team: Team): String? =
        // Gaeste tragen bei WordPress die 0; ohne diese Bedingung waere
        // jeder Gast das eigene Konto, sobald dessen Kennung fehlte.
        if (comment.authorId != 0L && comment.authorId == instance.userId) {
            Team.SELF
        } else {
            team.roleOf(comment.authorId)?.slug
        }

    override suspend fun hasBaseline(instance: WordPressInstance): Boolean =
        (dao.syncState(instance.id)?.lastNotifiedDateEpochMillis ?: 0L) > 0L

    /**
     * Hält den Stand fest.
     *
     * Läuft auch mit leerer Liste durch: Findet der erste Lauf nichts
     * Offenes, muss trotzdem ein Ausgangszustand entstehen. Sonst gälte der
     * nächste Lauf erneut als erster, und die erste echte Benachrichtigung
     * bliebe aus.
     */
    override suspend fun markNotified(instance: WordPressInstance, comments: List<Comment>) {
        val now = System.currentTimeMillis()

        if (comments.isNotEmpty()) {
            dao.markNotified(comments.map { NotifiedCommentEntity(instance.id, it.id, now) })
        }

        val current = dao.syncState(instance.id)
        val newestId = maxOf(comments.maxOfOrNull { it.id } ?: 0L, current?.lastNotifiedCommentId ?: 0L)
        val newestDate = maxOf(
            comments.maxOfOrNull { it.date.toEpochMilli() } ?: now,
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
