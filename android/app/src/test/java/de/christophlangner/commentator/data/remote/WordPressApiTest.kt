package de.christophlangner.commentator.data.remote

import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.data.account.ApplicationPassword
import de.christophlangner.commentator.data.account.CredentialSource
import de.christophlangner.commentator.data.account.InstanceCredentials
import de.christophlangner.commentator.data.remote.dto.UpdateCommentRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Prüft die Kommunikation mit der WordPress-REST-API gegen einen lokalen
 * Testserver: Parameter, Kopfzeilen, Paginierung und Fehlerantworten.
 */
class WordPressApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: WordPressApi
    private lateinit var executor: ApiExecutor
    private lateinit var sessionMonitor: SessionMonitor

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val credentials = CredentialSource {
        InstanceCredentials("moderator", ApplicationPassword("geheim"))
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        sessionMonitor = SessionMonitor()
        executor = ApiExecutor(json)

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor("instance-1", credentials, sessionMonitor))
            .build()

        api = Retrofit.Builder()
            .baseUrl(server.url("/wp-json/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(WordPressApi::class.java)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `Kommentarabfrage verwendet den edit-Kontext und die Filterwerte`() = runTest {
        server.enqueue(jsonResponse(200, "[]"))

        executor.call { api.listComments(status = "hold", page = 2, perPage = 20) }

        val request = server.takeRequest()
        val url = request.url
        assertEquals("hold", url.queryParameter("status"))
        assertEquals("2", url.queryParameter("page"))
        assertEquals("20", url.queryParameter("per_page"))
        // Ohne context=edit liefert WordPress weder Status noch E-Mail-Adresse.
        assertEquals("edit", url.queryParameter("context"))
        assertEquals("comment", url.queryParameter("type"))
    }

    @Test
    fun `Basic-Auth-Kopfzeile wird gesetzt`() = runTest {
        server.enqueue(jsonResponse(200, "[]"))

        executor.call { api.listComments(status = "all", page = 1, perPage = 10) }

        val request = server.takeRequest()
        assertEquals(
            InstanceCredentials("moderator", ApplicationPassword("geheim")).toBasicAuthHeader(),
            request.headers["Authorization"],
        )
    }

    @Test
    fun `Paginierung wird aus den Kopfzeilen gelesen`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-WP-Total", "57")
                .addHeader("X-WP-TotalPages", "3")
                .body(COMMENT_LIST)
                .build(),
        )

        val outcome = executor.call { api.listComments("hold", 1, 20) }

        val response = (outcome as Outcome.Success).value
        assertEquals(3, response.totalPages)
        assertEquals(57, response.totalItems)
        assertEquals(1, response.body.size)
    }

    @Test
    fun `fehlende Paginierungs-Kopfzeilen bedeuten eine einzige Seite`() = runTest {
        server.enqueue(jsonResponse(200, "[]"))

        val outcome = executor.call { api.listComments("hold", 1, 20) }

        assertEquals(1, (outcome as Outcome.Success).value.totalPages)
    }

    @Test
    fun `401 meldet die Sitzung als ungueltig`() = runTest {
        server.enqueue(jsonResponse(401, """{"code":"incorrect_password","data":{"status":401}}"""))

        val outcome = executor.call { api.listComments("hold", 1, 20) }

        assertEquals(AppError.Unauthorized, (outcome as Outcome.Failure).error)
        assertTrue(sessionMonitor.isInvalid("instance-1"))
    }

    @Test
    fun `erfolgreiche Antwort setzt die Sitzung wieder auf gueltig`() = runTest {
        server.enqueue(jsonResponse(401, """{"code":"incorrect_password"}"""))
        executor.call { api.listComments("hold", 1, 20) }
        assertTrue(sessionMonitor.isInvalid("instance-1"))

        server.enqueue(jsonResponse(200, "[]"))
        executor.call { api.listComments("hold", 1, 20) }

        assertTrue(!sessionMonitor.isInvalid("instance-1"))
    }

    @Test
    fun `403 unterscheidet sich von 401`() = runTest {
        server.enqueue(
            jsonResponse(403, """{"code":"rest_cannot_read","data":{"status":403}}"""),
        )

        val outcome = executor.call { api.listComments("hold", 1, 20) }

        assertEquals(AppError.Forbidden, (outcome as Outcome.Failure).error)
    }

    @Test
    fun `429 uebernimmt Retry-After`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(429)
                .addHeader("Retry-After", "42")
                .addHeader("Content-Type", "application/json")
                .body("{}")
                .build(),
        )

        val outcome = executor.call { api.listComments("hold", 1, 20) }

        assertEquals(AppError.RateLimited(42), (outcome as Outcome.Failure).error)
    }

    @Test
    fun `5xx wird als Serverfehler gemeldet`() = runTest {
        server.enqueue(jsonResponse(503, "{}"))

        val outcome = executor.call { api.listComments("hold", 1, 20) }

        assertEquals(AppError.ServerError(503), (outcome as Outcome.Failure).error)
    }

    @Test
    fun `WordPress-Fehlercode wird uebernommen`() = runTest {
        server.enqueue(
            jsonResponse(
                400,
                """{"code":"rest_comment_invalid_id","message":"Ungültig","data":{"status":400}}""",
            ),
        )

        val outcome = executor.call { api.getComment(99) }

        assertEquals(
            AppError.WordPress("rest_comment_invalid_id", 400),
            (outcome as Outcome.Failure).error,
        )
    }

    @Test
    fun `Statusaenderung sendet nur das Statusfeld`() = runTest {
        server.enqueue(jsonResponse(200, SINGLE_COMMENT))

        executor.call { api.updateComment(12, UpdateCommentRequest(status = "approved")) }

        val body = server.takeRequest().body?.utf8() ?: ""
        assertEquals("""{"status":"approved"}""", body)
    }

    @Test
    fun `Papierkorb und endgueltiges Loeschen unterscheiden sich im force-Parameter`() = runTest {
        server.enqueue(jsonResponse(200, "{}"))
        executor.callIgnoringBody { api.deleteComment(7, force = false) }
        assertEquals("false", server.takeRequest().url.queryParameter("force"))

        server.enqueue(jsonResponse(200, "{}"))
        executor.callIgnoringBody { api.deleteComment(7, force = true) }
        assertEquals("true", server.takeRequest().url.queryParameter("force"))
    }

    @Test
    fun `unlesbare Antwort ergibt einen Formatfehler statt eines Absturzes`() = runTest {
        server.enqueue(jsonResponse(200, "kein json"))

        val outcome = executor.call { api.getComment(1) }

        assertTrue(outcome is Outcome.Failure)
        assertEquals(AppError.MalformedResponse, (outcome as Outcome.Failure).error)
    }

    @Test
    fun `Bridge-Endpunkt wird korrekt angesprochen`() = runTest {
        server.enqueue(
            jsonResponse(
                200,
                """{"pending_count":4,"latest_comment_id":98,
                   "latest_comment_date_gmt":"2026-09-19T10:00:00","plugin_version":"1.0.0"}""",
            ),
        )

        val outcome = executor.call { api.bridgeStatus() }

        val body = (outcome as Outcome.Success).value.body
        assertEquals(4, body.pendingCount)
        assertEquals(98L, body.latestCommentId)
        assertTrue(server.takeRequest().url.encodedPath.endsWith("/commentator/v1/status"))
    }

    @Test
    fun `API-Wurzel meldet Autorisierungs-Endpunkt und Plugin`() = runTest {
        server.enqueue(jsonResponse(200, API_ROOT))

        val outcome = executor.call { api.index(server.url("/wp-json/").toString()) }

        val root = (outcome as Outcome.Success).value.body
        assertTrue(root.hasWpV2)
        assertTrue(root.hasBridgePlugin)
        assertEquals(
            "https://example.test/wp-admin/authorize-application.php",
            root.applicationPasswordAuthorizationEndpoint(),
        )
    }

    @Test
    fun `fehlende Application Passwords werden erkannt`() = runTest {
        server.enqueue(
            jsonResponse(200, """{"name":"Blog","namespaces":["wp/v2"],"authentication":{}}"""),
        )

        val outcome = executor.call { api.index(server.url("/wp-json/").toString()) }

        val root = (outcome as Outcome.Success).value.body
        assertTrue(root.hasWpV2)
        assertNull(root.applicationPasswordAuthorizationEndpoint())
    }

    private fun jsonResponse(code: Int, body: String) = MockResponse.Builder()
        .code(code)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private companion object {
        val SINGLE_COMMENT = """
            {"id":12,"post":3,"parent":0,"author":0,"author_name":"Max Mustermann",
             "author_email":"max@example.test","author_url":"",
             "date_gmt":"2026-09-19T08:15:00","content":{"rendered":"<p>Hallo</p>"},
             "status":"approved","type":"comment","link":"https://example.test/p/#comment-12"}
        """.trimIndent()

        val COMMENT_LIST = "[$SINGLE_COMMENT]"

        val API_ROOT = """
            {
              "name":"Testblog",
              "description":"",
              "url":"https://example.test",
              "home":"https://example.test",
              "namespaces":["wp/v2","commentator/v1"],
              "authentication":{
                "application-passwords":{
                  "endpoints":{
                    "authorization":"https://example.test/wp-admin/authorize-application.php"
                  }
                }
              }
            }
        """.trimIndent()
    }
}
