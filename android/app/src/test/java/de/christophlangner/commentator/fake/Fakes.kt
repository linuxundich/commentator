package de.christophlangner.commentator.fake

import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.core.net.ConnectivityObserver
import de.christophlangner.commentator.domain.model.CommentFilter
import de.christophlangner.commentator.domain.model.WordPressInstance
import de.christophlangner.commentator.domain.repository.AppSettings
import de.christophlangner.commentator.domain.repository.AuthRepository
import de.christophlangner.commentator.domain.model.RoleStyle
import de.christophlangner.commentator.domain.model.RoleStyles
import de.christophlangner.commentator.domain.model.Team
import de.christophlangner.commentator.domain.repository.TeamRepository
import de.christophlangner.commentator.domain.model.NotifyScope
import de.christophlangner.commentator.domain.repository.ReplyTemplate
import de.christophlangner.commentator.domain.repository.ReplyTemplateRepository
import de.christophlangner.commentator.domain.repository.SettingsRepository
import de.christophlangner.commentator.domain.repository.SiteDiscovery
import de.christophlangner.commentator.domain.repository.SiteSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeConnectivityObserver(online: Boolean = true) : ConnectivityObserver {
    val online = MutableStateFlow(online)
    override val isOnline: Flow<Boolean> = this.online
    override fun isCurrentlyOnline(): Boolean = online.value
}

class FakeAuthRepository(
    instance: WordPressInstance? = testInstance(),
    instances: List<WordPressInstance>? = null,
) : AuthRepository {

    val activeInstance = MutableStateFlow(instance)

    /** Alle Blogs; ohne Angabe genau der aktive, sofern es einen gibt. */
    val instances = MutableStateFlow(instances ?: listOfNotNull(instance))
    val sessionInvalid = MutableStateFlow(false)
    var discoveryResult: Outcome<SiteDiscovery> = Outcome.Failure(AppError.InvalidSite)
    var signInResult: Outcome<WordPressInstance> = Outcome.Success(testInstance())

    /** Die Kennungen, die abgemeldet wurden - in der Reihenfolge der Aufrufe. */
    val signedOutIds = mutableListOf<String>()
    val signedOut: Boolean get() = signedOutIds.isNotEmpty()

    override fun observeInstances(): Flow<List<WordPressInstance>> = instances

    override fun observeActiveInstance(): Flow<WordPressInstance?> = activeInstance

    override suspend fun setActiveInstance(instanceId: String) {
        instances.value.firstOrNull { it.id == instanceId }?.let { activeInstance.value = it }
    }

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

    override suspend fun refreshSiteCapabilities(instanceId: String): Outcome<WordPressInstance> {
        capabilitiesRefreshes++
        val updated = refreshedInstance
            // Der aktive Blog hat Vorrang vor der Liste: Tests setzen ihn
            // unmittelbar, und ohne Vorgabe soll ein Auffrischen nichts
            // aendern - so verhaelt sich auch der echte Blog, an dem sich
            // nichts geaendert hat.
            ?: activeInstance.value?.takeIf { it.id == instanceId }
            ?: instances.value.firstOrNull { it.id == instanceId }
            ?: return Outcome.Failure(AppError.Unauthorized)
        instances.value = instances.value.map { if (it.id == updated.id) updated else it }
        if (activeInstance.value?.id == updated.id) activeInstance.value = updated
        return Outcome.Success(updated)
    }

    override suspend fun signOut(instanceId: String) {
        signedOutIds += instanceId
        instances.value = instances.value.filterNot { it.id == instanceId }
        if (activeInstance.value?.id == instanceId) {
            activeInstance.value = instances.value.firstOrNull()
        }
    }
}

/**
 * Vorlagen je Blog.
 *
 * [state] ist der Stand des Blogs, den die Tests verwenden; welcher das ist,
 * spielt fuer sie keine Rolle - die Kennung wird nur mitgeschrieben, damit
 * sich pruefen laesst, dass sie ueberhaupt durchgereicht wird.
 */
class FakeReplyTemplateRepository(
    initial: List<ReplyTemplate> = emptyList(),
) : ReplyTemplateRepository {

    val state = MutableStateFlow(initial)

    /** Die Kennungen, mit denen zuletzt gearbeitet wurde. */
    val angefragteIds = mutableListOf<String>()

    override fun templates(instanceId: String): Flow<List<ReplyTemplate>> {
        angefragteIds += instanceId
        return state
    }

    override suspend fun add(instanceId: String, text: String): ReplyTemplate? {
        angefragteIds += instanceId
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        if (state.value.size >= ReplyTemplate.MAX_TEMPLATES) return null
        val template = ReplyTemplate(id = "t${state.value.size + 1}", text = trimmed)
        state.value = state.value + template
        return template
    }

    override suspend fun update(instanceId: String, id: String, text: String) {
        angefragteIds += instanceId
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        state.value = state.value.map { if (it.id == id) it.copy(text = trimmed) else it }
    }

    override suspend fun remove(instanceId: String, id: String) {
        angefragteIds += instanceId
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

    val invalidatedIds = mutableListOf<String>()

    override suspend fun invalidate(instanceId: String) {
        invalidations++
        invalidatedIds += instanceId
    }
}

/**
 * Einstellungen im Speicher.
 *
 * [state] traegt das Blogunabhaengige, [siteState] den Stand je Blog. Wer
 * keine Kennung unterscheiden will, nutzt [siteSettingsValue] - es liefert
 * denselben Stand fuer jeden Blog.
 */
class FakeSettingsRepository(
    initial: AppSettings = AppSettings(
        notificationsEnabled = true,
        syncIntervalMinutes = 15,
        showAvatars = false,
        showAuthorEmail = false,
        threadedInbox = true,
        lastFilter = CommentFilter.PENDING,
    ),
    /** Vorbelegung, die fuer jeden noch nicht gesetzten Blog gilt. */
    var siteDefault: SiteSettings = SiteSettings.DEFAULT,
) : SettingsRepository {

    val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state

    /** Der Stand je Blog, soweit er abweicht. */
    val siteState = MutableStateFlow<Map<String, SiteSettings>>(emptyMap())

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        state.value = state.value.copy(notificationsEnabled = enabled)
    }

    override suspend fun setSyncIntervalMinutes(minutes: Int) {
        state.value = state.value.copy(syncIntervalMinutes = minutes)
    }

    override suspend fun setShowAvatars(show: Boolean) {
        state.value = state.value.copy(showAvatars = show)
    }

    override suspend fun setShowAuthorEmail(show: Boolean) {
        state.value = state.value.copy(showAuthorEmail = show)
    }

    override suspend fun setThreadedInbox(threaded: Boolean) {
        state.value = state.value.copy(threadedInbox = threaded)
    }

    override suspend fun setLastFilter(filter: CommentFilter) {
        state.value = state.value.copy(lastFilter = filter)
    }

    override fun siteSettings(instanceId: String): Flow<SiteSettings> =
        siteState.map { it[instanceId] ?: siteDefault }

    /** Setzt den Stand eines Blogs unmittelbar, ohne Umweg über die Setter. */
    fun setSite(instanceId: String, settings: SiteSettings) {
        siteState.value = siteState.value + (instanceId to settings)
    }

    /** Der aktuelle Stand eines Blogs, wie ihn die App sehen würde. */
    fun siteSettingsValue(instanceId: String): SiteSettings =
        siteState.value[instanceId] ?: siteDefault

    private fun aendere(instanceId: String, block: (SiteSettings) -> SiteSettings) {
        setSite(instanceId, block(siteSettingsValue(instanceId)))
    }

    override suspend fun setSiteNotificationsEnabled(instanceId: String, enabled: Boolean) {
        aendere(instanceId) { it.copy(notificationsEnabled = enabled) }
    }

    override suspend fun setNotifyScope(instanceId: String, scope: NotifyScope) {
        aendere(instanceId) { it.copy(notifyScope = scope) }
    }

    override suspend fun setTeamRoles(instanceId: String, roles: Set<String>) {
        aendere(instanceId) { it.copy(teamRoles = roles) }
    }

    override suspend fun setRoleStyle(instanceId: String, slug: String, style: RoleStyle) {
        aendere(instanceId) { it.copy(roleStyles = it.roleStyles.with(slug, style)) }
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
