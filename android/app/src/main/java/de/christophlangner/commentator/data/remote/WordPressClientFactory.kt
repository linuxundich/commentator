package de.christophlangner.commentator.data.remote

import de.christophlangner.commentator.BuildConfig
import de.christophlangner.commentator.data.account.CredentialStore
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Liefert den API-Client zu einer Instanz.
 *
 * Eigene Schnittstelle, damit Repository und Hintergrundprüfung nicht an der
 * konkreten Client-Erzeugung hängen - und damit sie ohne Keystore und
 * DataStore geprüft werden können.
 */
fun interface WordPressApiProvider {
    fun forInstance(instanceId: String, siteUrl: String): WordPressApi
}

/**
 * Erzeugt einen API-Client je WordPress-Instanz.
 *
 * Bewusst kein Singleton-Client mit fester Basisadresse: Die Adresse gehört
 * zur Instanz, nicht zur App. Genau das macht den späteren Mehrfachbetrieb
 * möglich, ohne die Datenschicht anzufassen.
 */
@Singleton
class WordPressClientFactory @Inject constructor(
    private val baseClient: OkHttpClient,
    private val json: Json,
    private val credentialStore: CredentialStore,
    private val sessionMonitor: SessionMonitor,
) : WordPressApiProvider {

    private val clients = ConcurrentHashMap<String, WordPressApi>()

    override fun forInstance(instanceId: String, siteUrl: String): WordPressApi =
        clients.getOrPut(instanceId) {
            build(
                baseUrl = restBaseUrl(siteUrl),
                client = baseClient.newBuilder()
                    .addInterceptor(AuthInterceptor(instanceId, credentialStore, sessionMonitor))
                    .build(),
            )
        }

    /** Client ohne Zugangsdaten, für die Prüfung einer Adresse vor der Anmeldung. */
    fun anonymous(): WordPressApi = build("https://invalid.invalid/", baseClient)

    /**
     * Client mit fest übergebenen Zugangsdaten. Wird beim Abschluss der
     * Anmeldung gebraucht, bevor die Instanz überhaupt existiert.
     */
    fun withCredentials(siteUrl: String, basicAuthHeader: String): WordPressApi = build(
        baseUrl = restBaseUrl(siteUrl),
        client = baseClient.newBuilder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", basicAuthHeader)
                        .build(),
                )
            }
            .build(),
    )

    fun evict(instanceId: String) {
        clients.remove(instanceId)
    }

    private fun build(baseUrl: String, client: OkHttpClient): WordPressApi =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(WordPressApi::class.java)

    companion object {
        const val USER_AGENT = "Commentator/${BuildConfig.VERSION_NAME} (Android)"

        fun restBaseUrl(siteUrl: String): String = siteUrl.trimEnd('/') + "/wp-json/"
    }
}
