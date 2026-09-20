package de.christophlangner.commentator.integration

import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.data.account.ApplicationPassword
import de.christophlangner.commentator.data.account.CredentialSource
import de.christophlangner.commentator.data.account.InstanceCredentials
import de.christophlangner.commentator.data.remote.ApiExecutor
import de.christophlangner.commentator.data.remote.AuthInterceptor
import de.christophlangner.commentator.data.remote.SessionMonitor
import de.christophlangner.commentator.data.remote.WordPressApi
import de.christophlangner.commentator.data.remote.dto.CreateCommentRequest
import de.christophlangner.commentator.data.remote.dto.UpdateCommentRequest
import de.christophlangner.commentator.data.remote.mapper.CommentMapper
import de.christophlangner.commentator.domain.model.CommentStatus
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Integrationstest gegen die lokale WordPress-Testumgebung unter `docker/`.
 *
 * Der Test überspringt sich selbst, solange die Umgebungsvariablen nicht
 * gesetzt sind. Dadurch läuft er niemals unbeabsichtigt und schon gar nicht
 * gegen einen produktiven Blog - die Adresse muss ausdrücklich `localhost`
 * oder `127.0.0.1` sein, sonst bricht er ab.
 *
 * Ausführen:
 *
 * ```
 * cd docker && ./scripts/generate-cert.sh && docker compose up -d && ./scripts/seed.sh
 * cd ../android
 * COMMENTATOR_IT_URL=https://localhost:8443 \
 * COMMENTATOR_IT_USER=moderator \
 * COMMENTATOR_IT_PASSWORD='<Application Password aus seed.sh>' \
 *   ./gradlew :app:testDebugUnitTest --tests '*WordPressIntegrationTest'
 * ```
 */
class WordPressIntegrationTest {

    private val siteUrl: String? = System.getenv("COMMENTATOR_IT_URL")
    private val username: String? = System.getenv("COMMENTATOR_IT_USER")
    private val password: String? = System.getenv("COMMENTATOR_IT_PASSWORD")

    private lateinit var api: WordPressApi
    private lateinit var executor: ApiExecutor

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Before
    fun setUp() {
        assumeTrue(
            "Integrationstest übersprungen: COMMENTATOR_IT_* nicht gesetzt",
            !siteUrl.isNullOrBlank() && !username.isNullOrBlank() && !password.isNullOrBlank(),
        )
        // Schutzschranke gegen ein versehentlich gesetztes Produktivziel.
        assertTrue(
            "Integrationstests laufen ausschließlich gegen eine lokale Installation",
            siteUrl!!.contains("localhost") || siteUrl.contains("127.0.0.1"),
        )

        val credentials = CredentialSource {
            InstanceCredentials(username!!, ApplicationPassword(password!!))
        }

        val client = OkHttpClient.Builder()
            .apply { trustLocalSelfSignedCertificate() }
            .addInterceptor(AuthInterceptor("it", credentials, SessionMonitor()))
            .build()

        executor = ApiExecutor(json)
        api = Retrofit.Builder()
            .baseUrl("${siteUrl.trimEnd('/')}/wp-json/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(WordPressApi::class.java)
    }

    @Test
    fun `REST-API meldet wp-v2 und Application Passwords`() = runTest {
        val outcome = executor.call { api.index("${siteUrl!!.trimEnd('/')}/wp-json/") }

        val root = (outcome as Outcome.Success).value.body
        assertTrue(root.hasWpV2)
        assertTrue(
            "Ohne HTTPS bietet WordPress keine Application Passwords an",
            root.applicationPasswordAuthorizationEndpoint() != null,
        )
    }

    @Test
    fun `angemeldetes Konto darf moderieren`() = runTest {
        val outcome = executor.call { api.currentUser() }

        val user = (outcome as Outcome.Success).value.body
        assertTrue("Das Testkonto braucht moderate_comments", user.canModerateComments)
    }

    @Test
    fun `ausstehende Kommentare werden geliefert`() = runTest {
        val outcome = executor.call {
            api.listComments(status = CommentStatus.PENDING.queryValue, page = 1, perPage = 20)
        }

        val response = (outcome as Outcome.Success).value
        assertTrue("Die Testdaten enthalten offene Kommentare", response.body.isNotEmpty())
        response.body.forEach {
            assertEquals(CommentStatus.PENDING, CommentStatus.fromApi(it.status))
        }
    }

    @Test
    fun `Statuswechsel und Zuruecknahme funktionieren gegen die echte API`() = runTest {
        val pending = executor.call {
            api.listComments(status = CommentStatus.PENDING.queryValue, page = 1, perPage = 1)
        }
        val comment = (pending as Outcome.Success).value.body.firstOrNull()
        assumeTrue("Kein offener Kommentar vorhanden", comment != null)

        val approved = executor.call {
            api.updateComment(comment!!.id, UpdateCommentRequest(status = CommentStatus.APPROVED.writeValue))
        }
        assertEquals(
            CommentStatus.APPROVED,
            CommentStatus.fromApi((approved as Outcome.Success).value.body.status),
        )

        // Ausgangszustand wiederherstellen, damit der Test wiederholbar bleibt.
        val restored = executor.call {
            api.updateComment(comment!!.id, UpdateCommentRequest(status = CommentStatus.PENDING.writeValue))
        }
        assertEquals(
            CommentStatus.PENDING,
            CommentStatus.fromApi((restored as Outcome.Success).value.body.status),
        )
    }

    @Test
    fun `Antwort wird als Kommentar mit Bezug veroeffentlicht`() = runTest {
        val pending = executor.call {
            api.listComments(status = CommentStatus.PENDING.queryValue, page = 1, perPage = 1)
        }
        val parent = (pending as Outcome.Success).value.body.firstOrNull()
        assumeTrue("Kein offener Kommentar vorhanden", parent != null)

        val created = executor.call {
            api.createComment(
                CreateCommentRequest(
                    post = parent!!.post,
                    parent = parent.id,
                    content = "Antwort aus dem Integrationstest",
                    status = CommentStatus.APPROVED.writeValue,
                ),
            )
        }

        val reply = CommentMapper.toDomain((created as Outcome.Success).value.body, "it")
        assertEquals(parent!!.id, reply.parentId)
        assertTrue(reply.isReply)

        // Aufräumen: Die Antwort wird endgültig entfernt.
        val deleted = executor.callIgnoringBody { api.deleteComment(reply.id, force = true) }
        assertTrue(deleted is Outcome.Success)
    }

    @Test
    fun `Bridge-Plugin liefert den Zustand`() = runTest {
        val outcome = executor.call { api.bridgeStatus() }
        assumeTrue("Plugin commentator-bridge nicht aktiv", outcome is Outcome.Success)

        val status = (outcome as Outcome.Success).value.body
        assertTrue(status.pendingCount >= 0)
        assertEquals("1.0.0", status.pluginVersion)
    }

    /**
     * Die Testumgebung verwendet ein selbstsigniertes Zertifikat. Das wird
     * ausschließlich hier akzeptiert - die App selbst tut das nie, und der
     * Test läuft ohnehin nur gegen localhost.
     */
    private fun OkHttpClient.Builder.trustLocalSelfSignedCertificate() {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf(trustManager), java.security.SecureRandom())
        sslSocketFactory(context.socketFactory, trustManager)
        hostnameVerifier { _, _ -> true }
    }
}
