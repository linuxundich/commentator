package de.christophlangner.commentator.fake

import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.core.net.ConnectivityObserver
import de.christophlangner.commentator.domain.model.WordPressInstance
import de.christophlangner.commentator.domain.repository.AppSettings
import de.christophlangner.commentator.domain.repository.AuthRepository
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

    override suspend fun signOut() {
        signedOut = true
        activeInstance.value = null
    }
}

class FakeSettingsRepository(
    initial: AppSettings = AppSettings(
        notificationsEnabled = true,
        syncIntervalMinutes = 15,
        showAvatars = false,
        showAuthorEmail = false,
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
