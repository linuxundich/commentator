package de.christophlangner.commentator.fake

import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.core.net.ConnectivityObserver
import de.christophlangner.commentator.domain.model.WordPressInstance
import de.christophlangner.commentator.domain.repository.AppSettings
import de.christophlangner.commentator.domain.repository.AuthRepository
import de.christophlangner.commentator.domain.model.Team
import de.christophlangner.commentator.domain.repository.TeamRepository
import de.christophlangner.commentator.domain.model.NotifyScope
import de.christophlangner.commentator.domain.repository.ReplyTemplate
import de.christophlangner.commentator.domain.repository.ReplyTemplateRepository
import de.christophlangner.commentator.domain.repository.SettingsRepository
import de.christophlangner.commentator.domain.repository.SiteDiscovery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeConnectivityObserver(online: Boolean = true) : ConnectivityObserver {
    val online = MutableStateFlow(online)
    override val isOnline: Flow<Boolean> = this.online
    override fun isCurrentlyOnline(): Boolean = online.value
}

class FakeAuthRepository(
    instance: WordPressInstance? = testInstance(),
) : AuthRepository {

    val activeInstance = MutableStateFlow(instance)
    val sessionInvalid = MutableStateFlow(false)
    var discoveryResult: Outcome<SiteDiscovery> = Outcome.Failure(AppError.InvalidSite)
    var signInResult: Outcome<WordPressInstance> = Outcome.Success(testInstance())
    var signedOut = false

    override fun observeActiveInstance(): Flow<WordPressInstance?> = activeInstance

    override fun observeSessionInvalid(): Flow<Boolean> = sessionInvalid

    override suspend fun discoverSite(rawUrl: String): Outcome<SiteDiscovery> = discoveryResult

    override fun authorizationUrl(discovery: SiteDiscovery): String? =
        discovery.authorizationEndpoint?.plus("?app_name=Commentator")

    override suspend fun completeSignIn(
        siteUrl: String,
        username: String,
        applicationPassword: String,
    ): Outcome<WordPressInstance> = signInResult

    var capabilitiesRefreshes = 0

    /** Was eine Neubewertung liefert; standardmaessig bleibt alles, wie es ist. */
    var refreshedInstance: WordPressInstance? = null

    override suspend fun refreshSiteCapabilities(): Outcome<WordPressInstance> {
        capabilitiesRefreshes++
        val updated = refreshedInstance ?: activeInstance.value
            ?: return Outcome.Failure(AppError.Unauthorized)
        activeInstance.value = updated
        return Outcome.Success(updated)
    }

    override suspend fun signOut() {
        signedOut = true
        activeInstance.value = null
    }
}

class FakeReplyTemplateRepository(
    initial: List<ReplyTemplate> = emptyList(),
) : ReplyTemplateRepository {

    val state = MutableStateFlow(initial)

    override val templates: Flow<List<ReplyTemplate>> = state

    override suspend fun add(text: String): ReplyTemplate? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        if (state.value.size >= ReplyTemplate.MAX_TEMPLATES) return null
        val template = ReplyTemplate(id = "t${state.value.size + 1}", text = trimmed)
        state.value = state.value + template
        return template
    }

    override suspend fun update(id: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        state.value = state.value.map { if (it.id == id) it.copy(text = trimmed) else it }
    }

    override suspend fun remove(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }
}

class FakeTeamRepository(
    var team: Team = Team(),
) : TeamRepository {
    var calls = 0

    var invalidations = 0

    override suspend fun team(instanceId: String): Outcome<Team> {
        calls++
        return Outcome.Success(team)
    }

    override suspend fun invalidate() {
        invalidations++
    }
}

class FakeSettingsRepository(
    initial: AppSettings = AppSettings(
        notificationsEnabled = true,
        syncIntervalMinutes = 15,
        showAvatars = false,
        showAuthorEmail = false,
        notifyScope = NotifyScope.DEFAULT,
        teamRoles = Team.DEFAULT_ROLES,
        hideTeamComments = false,
    ),
) : SettingsRepository {

    val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        state.value = state.value.copy(notificationsEnabled = enabled)
    }

    override suspend fun setSyncIntervalMinutes(minutes: Int) {
        state.value = state.value.copy(syncIntervalMinutes = minutes)
    }

    override suspend fun setShowAvatars(show: Boolean) {
        state.value = state.value.copy(showAvatars = show)
    }

    override suspend fun setNotifyScope(scope: NotifyScope) {
        state.value = state.value.copy(notifyScope = scope)
    }

    override suspend fun setTeamRoles(roles: Set<String>) {
        state.value = state.value.copy(teamRoles = roles)
    }

    override suspend fun setHideTeamComments(hide: Boolean) {
        state.value = state.value.copy(hideTeamComments = hide)
    }

    override suspend fun setShowAuthorEmail(show: Boolean) {
        state.value = state.value.copy(showAuthorEmail = show)
    }
}

fun testInstance(
    id: String = "instance-1",
    canModerate: Boolean = true,
    hasBridgePlugin: Boolean = false,
) = WordPressInstance(
    id = id,
    displayName = "Testblog",
    siteUrl = "https://example.test",
    username = "moderator",
    userId = 2,
    canModerate = canModerate,
    hasBridgePlugin = hasBridgePlugin,
)
