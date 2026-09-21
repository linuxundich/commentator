package de.christophlangner.commentator.data.repository

import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.core.map
import de.christophlangner.commentator.data.account.InstanceStore
import de.christophlangner.commentator.data.remote.ApiExecutor
import de.christophlangner.commentator.data.remote.WordPressApiProvider
import de.christophlangner.commentator.domain.model.Team
import de.christophlangner.commentator.domain.model.TeamRole
import de.christophlangner.commentator.domain.repository.SettingsRepository
import de.christophlangner.commentator.domain.repository.TeamRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultTeamRepository @Inject constructor(
    private val clientFactory: WordPressApiProvider,
    private val executor: ApiExecutor,
    private val instanceStore: InstanceStore,
    private val settingsRepository: SettingsRepository,
) : TeamRepository {

    override suspend fun team(instanceId: String): Outcome<Team> {
        val instance = instanceStore.currentActive()?.takeIf { it.id == instanceId }
            ?: return Outcome.Failure(AppError.Unauthorized)
        val selected = settingsRepository.settings.first().teamRoles

        if (!instance.hasBridgePlugin) {
            // Ohne Plugin bleibt nur das eigene Konto. Besser als nichts: Die
            // eigenen Antworten sind der häufigste Fall überhaupt.
            return Outcome.Success(Team(memberIds = setOf(instance.userId)))
        }

        val api = clientFactory.forInstance(instance.id, instance.siteUrl)
        return executor.call { api.bridgeTeam() }.map { response ->
            val body = response.body
            Team(
                memberIds = body.members
                    .filter { member -> member.roles.any { it in selected } }
                    .map { it.id }
                    .toSet()
                    // Das eigene Konto gehört immer dazu, auch wenn seine
                    // Rolle gerade nicht ausgewählt ist.
                    .plus(instance.userId),
                availableRoles = body.roles.map { TeamRole(it.slug, it.name) },
            )
        }
    }
}
