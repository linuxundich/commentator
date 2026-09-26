package de.christophlangner.commentator.data.repository

import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.core.net.SiteIcon
import de.christophlangner.commentator.core.net.SiteUrl
import de.christophlangner.commentator.data.account.ApplicationPassword
import de.christophlangner.commentator.data.account.CredentialStore
import de.christophlangner.commentator.data.account.InstanceCredentials
import de.christophlangner.commentator.data.account.InstanceStore
import de.christophlangner.commentator.data.local.dao.CommentDao
import de.christophlangner.commentator.data.remote.ApiExecutor
import de.christophlangner.commentator.data.remote.SessionMonitor
import de.christophlangner.commentator.data.remote.WordPressApi
import de.christophlangner.commentator.data.remote.WordPressClientFactory
import de.christophlangner.commentator.domain.model.WordPressInstance
import de.christophlangner.commentator.domain.repository.AuthRepository
import de.christophlangner.commentator.domain.repository.SiteDiscovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.ResponseBody
import okio.Buffer
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

    override fun observeInstances(): Flow<List<WordPressInstance>> = instanceStore.instances

    override fun observeActiveInstance(): Flow<WordPressInstance?> = instanceStore.activeInstance

    override suspend fun setActiveInstance(instanceId: String) {
        instanceStore.setActive(instanceId)
    }

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

        // Ist die Adresse schon eingerichtet, behaelt der Eintrag seine
        // Kennung. Daran haengen der Zwischenspeicher und der Meldestand: Ein
        // zweiter Eintrag fuer denselben Blog wuerde nicht nur doppelt in der
        // Liste stehen, er wuerde auch jeden vorhandenen Kommentar noch einmal
        // als neu melden. Eine erneute Anmeldung ist deshalb ein Wechsel der
        // Zugangsdaten, keine zweite Einrichtung - auch dann, wenn dabei ein
        // anderes Konto verwendet wird.
        val bestehend = instanceStore.byUrl(normalized)

        val instance = WordPressInstance(
            id = bestehend?.id ?: UUID.randomUUID().toString(),
            displayName = index?.name?.ifBlank { normalized } ?: normalized,
            siteUrl = normalized,
            username = credentials.username,
            userId = user.id,
            canModerate = user.canModerateComments,
            hasBridgePlugin = index?.hasBridgePlugin == true,
            canManageOptions = user.canManageOptions,
            iconUrl = index?.iconUrl,
            // Nur nachsehen, wenn der Blog kein Site-Icon gesetzt hat.
            themeIconUrl = if (index?.iconUrl == null) themeIcon(api, normalized) else null,
        )

        credentialStore.save(instance.id, credentials)
        instanceStore.upsert(instance)
        sessionMonitor.reportAuthorized(instance.id)
        return Outcome.Success(instance)
    }

    override suspend fun refreshSiteCapabilities(instanceId: String): Outcome<WordPressInstance> {
        val instance = instanceStore.byId(instanceId)
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
            canManageOptions = user?.canManageOptions ?: instance.canManageOptions,
            // Das Site-Icon kann im Blog entfernt worden sein - dann soll es
            // auch hier verschwinden, nicht der alte Wert stehen bleiben.
            iconUrl = root.iconUrl,
            // Der Fund aus dem Seitenkopf bleibt stehen, sobald er einmal da
            // ist: Dafuer jedes Mal die ganze Startseite zu holen waere ein
            // hoher Preis fuer ein Symbol, das sich selten aendert.
            themeIconUrl = when {
                root.iconUrl != null -> instance.themeIconUrl
                instance.themeIconUrl != null -> instance.themeIconUrl
                else -> themeIcon(api, instance.siteUrl)
            },
            hasBridgePlugin = root.hasBridgePlugin,
        )
        // Ohne makeActive=false wuerde das Auffrischen eines Blogs im
        // Hintergrund den angezeigten wechseln.
        if (updated != instance) instanceStore.upsert(updated, makeActive = false)
        return Outcome.Success(updated)
    }

    /**
     * Das Symbol aus dem Seitenkopf, wenn kein Site-Icon gesetzt ist.
     *
     * Viele Blogs bringen ihr Symbol im Theme mit und tragen es nur als
     * `<link rel="icon">` ein - fuer die REST-API ist es dann unsichtbar,
     * obwohl es im Browser ueberall auftaucht.
     *
     * Scheitert der Abruf, gibt es eben kein Symbol. Ein Fehler hier darf
     * weder die Anmeldung noch das Auffrischen zum Scheitern bringen.
     */
    private suspend fun themeIcon(api: WordPressApi, siteUrl: String): String? {
        val antwort = executor.call { api.homePage(siteUrl) }.valueOrNull?.body ?: return null

        // Zwingend auf einem Hintergrund-Thread: Anders als bei den uebrigen
        // Aufrufen liest hier nicht Retrofit den Koerper, sondern diese
        // Methode - und zwar blockierend. Fortgesetzt wird sie aber auf dem
        // Dispatcher des Aufrufers, und das ist ueber viewModelScope der
        // Hauptthread. Ohne withContext fliegt eine
        // NetworkOnMainThreadException, und zwar auch beim Schliessen, weil
        // ein nicht zu Ende gelesener Koerper dabei die Verbindung leerraeumt.
        // Die Absicherung liegt um das Schliessen herum, nicht nur um das
        // Lesen: Ein nicht zu Ende gelesener Koerper raeumt beim Schliessen
        // die Verbindung leer, und auch dabei kann es schiefgehen. Eine
        // Ausnahme von hier wuerde sonst das Auffrischen mitreissen - wegen
        // eines Symbols.
        val kopf = withContext(Dispatchers.IO) {
            runCatching { antwort.use { it.kopfbereich(MAX_KOPF_BYTES) } }.getOrNull()
        } ?: return null

        return SiteIcon.fromHtml(kopf, siteUrl)
    }

    /**
     * Liest hoechstens [maxBytes] aus dem Koerper.
     *
     * Die Verweise auf das Symbol stehen am Ende des Kopfbereichs - auf einem
     * echten Blog nachgemessen bei Byte 63.000, kurz vor `</head>` bei 64.758.
     * Ein knapperes Limit haette sie um Haaresbreite verfehlt.
     */
    private fun ResponseBody.kopfbereich(maxBytes: Long): String {
        val quelle = source()
        val puffer = Buffer()
        while (puffer.size < maxBytes) {
            if (quelle.read(puffer, maxBytes - puffer.size) == -1L) break
        }
        return puffer.readUtf8()
    }

    override suspend fun signOut(instanceId: String) {
        val instance = instanceStore.byId(instanceId) ?: return
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
        if (instanceStore.all().isEmpty()) {
            credentialStore.destroyKeyMaterial()
        }
    }

    companion object {
        const val APP_NAME = "Commentator"

        /** Feste Kennung der Anwendung, damit WordPress wiederholte Autorisierungen zuordnen kann. */
        const val APP_ID = "0c3f9fcb-3cb9-4c7f-9a62-9e63a5f5f6a1"

        const val SUCCESS_URL = "commentator://auth-callback"
        const val REJECT_URL = "commentator://auth-rejected"

        /**
         * Wie viel von der Startseite hoechstens gelesen wird.
         *
         * Nur der Kopfbereich wird gebraucht. 256 KB lassen genug Luft: Auf
         * einem echten Blog endete er bei Byte 64.758, und die Verweise auf
         * das Symbol standen erst bei 63.000.
         */
        private const val MAX_KOPF_BYTES = 256L * 1024
    }
}
