package de.christophlangner.commentator.data.repository

import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.core.net.SiteUrl
import de.christophlangner.commentator.data.account.ApplicationPassword
import de.christophlangner.commentator.data.account.CredentialStore
import de.christophlangner.commentator.data.account.InstanceCredentials
import de.christophlangner.commentator.data.account.InstanceStore
import de.christophlangner.commentator.data.local.dao.CommentDao
import de.christophlangner.commentator.data.remote.ApiExecutor
import de.christophlangner.commentator.data.remote.SessionMonitor
import de.christophlangner.commentator.data.remote.WordPressClientFactory
import de.christophlangner.commentator.domain.model.WordPressInstance
import de.christophlangner.commentator.domain.repository.AuthRepository
import de.christophlangner.commentator.domain.repository.SiteDiscovery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Einrichtung über Application Passwords.
 *
 * Das Kontokennwort des Benutzers berührt die App zu keinem Zeitpunkt: Es
 * wird ausschließlich im Browser eingegeben, und zurück kommt nur ein
 * einzeln widerrufbares Application Password.
 */
@Singleton
class DefaultAuthRepository @Inject constructor(
    private val clientFactory: WordPressClientFactory,
    private val executor: ApiExecutor,
    private val credentialStore: CredentialStore,
    private val instanceStore: InstanceStore,
    private val sessionMonitor: SessionMonitor,
    private val dao: CommentDao,
) : AuthRepository {

    override fun observeActiveInstance(): Flow<WordPressInstance?> = instanceStore.activeInstance

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeSessionInvalid(): Flow<Boolean> =
        instanceStore.activeInstance.flatMapLatest { instance ->
            if (instance == null) flowOf(false) else sessionMonitor.observeInvalid(instance.id)
        }

    override suspend fun discoverSite(rawUrl: String): Outcome<SiteDiscovery> {
        val siteUrl = SiteUrl.normalize(rawUrl)
            ?: return Outcome.Failure(AppError.InsecureSiteUrl)

        val api = clientFactory.anonymous()
        val result = executor.call { api.index(WordPressClientFactory.restBaseUrl(siteUrl)) }

        return when (result) {
            is Outcome.Failure -> when (result.error) {
                // Ein 404 an dieser Stelle heißt nicht "Seite fehlt", sondern
                // "hier läuft keine erreichbare REST-API".
                AppError.NotFound, AppError.MalformedResponse ->
                    Outcome.Failure(AppError.InvalidSite)

                else -> result
            }

            is Outcome.Success -> {
                val root = result.value.body
                if (!root.hasWpV2) {
                    Outcome.Failure(AppError.InvalidSite)
                } else {
                    Outcome.Success(
                        SiteDiscovery(
                            siteUrl = siteUrl,
                            siteName = root.name.ifBlank { siteUrl },
                            authorizationEndpoint = root.applicationPasswordAuthorizationEndpoint(),
                        ),
                    )
                }
            }
        }
    }

    override fun authorizationUrl(discovery: SiteDiscovery): String? {
        val endpoint = discovery.authorizationEndpoint?.toHttpUrlOrNull() ?: return null
        return endpoint.newBuilder()
            .addQueryParameter("app_name", APP_NAME)
            .addQueryParameter("app_id", APP_ID)
            .addQueryParameter("success_url", SUCCESS_URL)
            .addQueryParameter("reject_url", REJECT_URL)
            .build()
            .toString()
    }

    override suspend fun completeSignIn(
        siteUrl: String,
        username: String,
        applicationPassword: String,
    ): Outcome<WordPressInstance> {
        val normalized = SiteUrl.normalize(siteUrl)
            ?: return Outcome.Failure(AppError.InsecureSiteUrl)

        val credentials = InstanceCredentials(
            username = username.trim(),
            // WordPress zeigt Application Passwords in Vierergruppen an; die
            // Leerzeichen gehören nicht zum Wert.
            password = ApplicationPassword(applicationPassword.replace(" ", "").trim()),
        )
        if (credentials.username.isBlank() || credentials.password.isBlank) {
            return Outcome.Failure(AppError.Unauthorized)
        }

        val api = clientFactory.withCredentials(normalized, credentials.toBasicAuthHeader())

        val user = when (val result = executor.call { api.currentUser() }) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.value.body
        }

        val index = executor.call { api.index(WordPressClientFactory.restBaseUrl(normalized)) }
            .valueOrNull?.body

        val instance = WordPressInstance(
            id = UUID.randomUUID().toString(),
            displayName = index?.name?.ifBlank { normalized } ?: normalized,
            siteUrl = normalized,
            username = credentials.username,
            userId = user.id,
            canModerate = user.canModerateComments,
            hasBridgePlugin = index?.hasBridgePlugin == true,
        )

        credentialStore.save(instance.id, credentials)
        instanceStore.upsert(instance)
        sessionMonitor.reportAuthorized(instance.id)
        return Outcome.Success(instance)
    }

    override suspend fun refreshSiteCapabilities(): Outcome<WordPressInstance> {
        val instance = instanceStore.currentActive()
            ?: return Outcome.Failure(AppError.Unauthorized)
        val api = clientFactory.forInstance(instance.id, instance.siteUrl)

        val index = executor.call { api.index(WordPressClientFactory.restBaseUrl(instance.siteUrl)) }
        if (index is Outcome.Failure) return index

        // Das Moderationsrecht kommt aus einem anderen Endpunkt. Scheitert der,
        // bleibt der bisher bekannte Wert stehen, statt das Recht stillschweigend
        // zu entziehen.
        val user = executor.call { api.currentUser() }.valueOrNull?.body

        val root = (index as Outcome.Success).value.body
        val updated = instance.copy(
            displayName = root.name.ifBlank { instance.displayName },
            canModerate = user?.canModerateComments ?: instance.canModerate,
            hasBridgePlugin = root.hasBridgePlugin,
        )
        if (updated != instance) instanceStore.upsert(updated)
        return Outcome.Success(updated)
    }

    override suspend fun signOut() {
        val instance = instanceStore.currentActive() ?: return
        credentialStore.clear(instance.id)
        instanceStore.remove(instance.id)
        clientFactory.evict(instance.id)
        sessionMonitor.reportAuthorized(instance.id)

        dao.deleteAllComments(instance.id)
        dao.deletePostTitles(instance.id)
        dao.deleteSyncState(instance.id)
        dao.deleteNotified(instance.id)

        // Der Keystore-Schlüssel wird von allen Instanzen geteilt und darf erst
        // verschwinden, wenn keine mehr eingerichtet ist.
        if (instanceStore.currentActive() == null) {
            credentialStore.destroyKeyMaterial()
        }
    }

    companion object {
        const val APP_NAME = "Commentator"

        /** Feste Kennung der Anwendung, damit WordPress wiederholte Autorisierungen zuordnen kann. */
        const val APP_ID = "0c3f9fcb-3cb9-4c7f-9a62-9e63a5f5f6a1"

        const val SUCCESS_URL = "commentator://auth-callback"
        const val REJECT_URL = "commentator://auth-rejected"
    }
}
