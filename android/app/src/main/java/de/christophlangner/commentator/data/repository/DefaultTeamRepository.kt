package de.christophlangner.commentator.data.repository

import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.core.map
import de.christophlangner.commentator.data.account.InstanceStore
import de.christophlangner.commentator.data.local.dao.CommentDao
import de.christophlangner.commentator.data.local.entity.TeamMemberEntity
import de.christophlangner.commentator.data.local.entity.TeamRoleEntity
import de.christophlangner.commentator.data.remote.ApiExecutor
import de.christophlangner.commentator.data.remote.WordPressApiProvider
import de.christophlangner.commentator.domain.model.Team
import de.christophlangner.commentator.domain.model.TeamRole
import de.christophlangner.commentator.domain.repository.SettingsRepository
import de.christophlangner.commentator.domain.repository.TeamRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultTeamRepository @Inject constructor(
    private val clientFactory: WordPressApiProvider,
    private val executor: ApiExecutor,
    private val instanceStore: InstanceStore,
    private val settingsRepository: SettingsRepository,
    private val dao: CommentDao,
) : TeamRepository {

    private companion object {
        /**
         * Ersatzrolle, wenn die tatsaechliche nicht ermittelbar ist.
         *
         * Ohne Namen: Wie die Oberflaeche das eigene Konto benennt, ist
         * uebersetzter Text und gehoert nicht hierher.
         */
        val EIGENES_KONTO = TeamRole(slug = Team.SELF, name = "")
    }

    /**
     * Zuletzt geholtes Team je Instanz und Rollenauswahl.
     *
     * Ohne das fragte jede Zaehlung und jede Seite erneut nach - beim
     * Ausblenden des Teams waeren das ein halbes Dutzend zusaetzlicher
     * Anfragen je Aktualisierung. Wer zum Team gehoert, aendert sich
     * waehrenddessen nicht.
     */
    private val cache = ConcurrentHashMap<String, Team>()

    override fun observeTeam(instanceId: String): Flow<Team> = combine(
        dao.observeTeamMembers(instanceId),
        dao.observeTeamRoles(instanceId),
    ) { mitglieder, rollen ->
        Team(
            members = mitglieder.associate { it.userId to TeamRole(it.roleSlug, it.roleName) },
            availableRoles = rollen.map { TeamRole(it.slug, it.name) },
        )
    }

    override suspend fun invalidate(instanceId: String) {
        cache.keys.removeAll { it.substringBefore('/') == instanceId }
        // Auch der gespeicherte Stand: Er gilt je Rollenauswahl, und nach
        // deren Aenderung waere er schlicht falsch. Lieber kurz keine
        // Rollenmarke als eine, die nicht mehr stimmt.
        dao.replaceTeam(instanceId, emptyList(), emptyList())
    }

    override suspend fun team(instanceId: String): Outcome<Team> {
        // Nachgesehen wird in der ganzen Liste, nicht nur beim angezeigten
        // Blog: Die Hintergrundpruefung geht alle durch, und fuer die
        // uebrigen gaebe es sonst nie ein Team.
        val instance = instanceStore.byId(instanceId)
            ?: return Outcome.Failure(AppError.Unauthorized)
        val selected = settingsRepository.siteSettings(instanceId).first().teamRoles
        val key = instanceId + "/" + selected.sorted().joinToString(",")
        cache[key]?.let { return Outcome.Success(it) }

        if (!instance.hasBridgePlugin) {
            // Ohne Plugin bleibt nur das eigene Konto. Besser als nichts: Die
            // eigenen Antworten sind der häufigste Fall überhaupt. Die Rolle
            // ist dabei unbekannt.
            val team = Team(members = mapOf(instance.userId to EIGENES_KONTO))
            festhalten(instanceId, team)
            cache[key] = team
            return Outcome.Success(team)
        }

        val api = clientFactory.forInstance(instance.id, instance.siteUrl)
        val outcome = executor.call { api.bridgeTeam() }.map { response ->
            val body = response.body
            val bekannteRollen = body.roles.associate { it.slug to TeamRole(it.slug, it.name) }

            val members = body.members.mapNotNull { member ->
                // Die erste passende Rolle bestimmt das Aussehen. Wer mehrere
                // hat, wird nach der höchsten ausgezeichnet - die Reihenfolge
                // des Plugins beginnt beim Administrator.
                val rolle = member.roles.firstOrNull { it in selected }
                    ?.let { bekannteRollen[it] }
                rolle?.let { member.id to it }
            }.toMap()

            Team(
                // Das eigene Konto gehört immer dazu, auch wenn seine Rolle
                // gerade nicht ausgewählt ist.
                members = members + (instance.userId to (members[instance.userId] ?: EIGENES_KONTO)),
                availableRoles = bekannteRollen.values.toList(),
            )
        }

        return when (outcome) {
            is Outcome.Success -> {
                festhalten(instanceId, outcome.value)
                cache[key] = outcome.value
                outcome
            }
            // Ohne Verbindung der zuletzt bekannte Stand. Sonst galte
            // niemand als Team: Die Liste verloere ihre Rollenmarken, und
            // die Hintergrundpruefung meldete ausgerechnet die Rollen, die
            // stummgeschaltet sind. Nicht in den Arbeitsspeicher gelegt - der
            // naechste Versuch soll es wieder beim Blog probieren.
            is Outcome.Failure -> gespeichertesTeam(instanceId)?.let { Outcome.Success(it) }
                ?: outcome
        }
    }

    private suspend fun festhalten(instanceId: String, team: Team) {
        dao.replaceTeam(
            instanceId = instanceId,
            members = team.members.map { (userId, rolle) ->
                TeamMemberEntity(
                    instanceId = instanceId,
                    userId = userId,
                    roleSlug = rolle.slug,
                    roleName = rolle.name,
                )
            },
            roles = team.availableRoles.map { TeamRoleEntity(instanceId, it.slug, it.name) },
        )
    }

    /** Der gespeicherte Stand, oder `null` wenn noch nie einer geholt wurde. */
    private suspend fun gespeichertesTeam(instanceId: String): Team? {
        val mitglieder = dao.teamMembers(instanceId)
        if (mitglieder.isEmpty()) return null
        return Team(
            members = mitglieder.associate { it.userId to TeamRole(it.roleSlug, it.roleName) },
            availableRoles = dao.teamRoles(instanceId).map { TeamRole(it.slug, it.name) },
        )
    }
}
