package de.christophlangner.commentator.notification

import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.data.local.entity.SyncStateEntity
import de.christophlangner.commentator.data.remote.ApiExecutor
import de.christophlangner.commentator.data.remote.WordPressApi
import de.christophlangner.commentator.data.remote.WordPressApiProvider
import de.christophlangner.commentator.fake.FakeCommentDao
import de.christophlangner.commentator.domain.model.NotifyScope
import de.christophlangner.commentator.domain.model.RoleStyles
import de.christophlangner.commentator.domain.model.Team
import de.christophlangner.commentator.domain.model.TeamRole
import de.christophlangner.commentator.fake.FakeSettingsRepository
import de.christophlangner.commentator.fake.FakeTeamRepository
import de.christophlangner.commentator.fake.testInstance
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Die Entdopplung ist der heikle Teil der Benachrichtigungen: Ein zweimal
 * gemeldeter Kommentar ist aus Nutzersicht ein Fehler.
 */
// Robolectric, weil das Mapping die Android-Plattform für die Umwandlung
// von HTML in reinen Text verwendet.
@RunWith(RobolectricTestRunner::class)
class PollingNewCommentSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: PollingNewCommentSource
    private val dao = FakeCommentDao()
    private val settings = FakeSettingsRepository()
    private val teamRepo = FakeTeamRepository()
    private val instance = testInstance()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val api = Retrofit.Builder()
            .baseUrl(server.url("/wp-json/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(WordPressApi::class.java)

        val provider = WordPressApiProvider { _, _ -> api }
        source = PollingNewCommentSource(provider, ApiExecutor(json), dao, settings, teamRepo)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `liefert neue Kommentare aufsteigend nach Datum`() = runTest {
        server.enqueue(jsonResponse(comments(listOf(11L to "2026-09-19T10:00:00", 12L to "2026-09-19T11:00:00"))))

        val outcome = source.fetchUnnotified(instance)

        val comments = (outcome as Outcome.Success).value.toReport
        assertEquals(listOf(11L, 12L), comments.map { it.id })
    }

    @Test
    fun `bereits gemeldete Kommentare erscheinen nicht erneut`() = runTest {
        server.enqueue(jsonResponse(comments(listOf(11L to "2026-09-19T10:00:00"))))
        val first = (source.fetchUnnotified(instance) as Outcome.Success).value
        source.markNotified(instance, first.all)

        server.enqueue(jsonResponse(comments(listOf(11L to "2026-09-19T10:00:00"))))
        val second = source.fetchUnnotified(instance)

        assertTrue((second as Outcome.Success).value.all.isEmpty())
    }

    @Test
    fun `aeltere Kommentare als der zuletzt gemeldete werden ignoriert`() = runTest {
        dao.upsertSyncState(
            SyncStateEntity(
                instanceId = instance.id,
                lastSyncEpochMillis = null,
                lastNotifiedCommentId = 20,
                lastNotifiedDateEpochMillis = 0,
            ),
        )
        server.enqueue(jsonResponse(comments(listOf(19L to "2026-09-19T10:00:00", 21L to "2026-09-19T12:00:00"))))

        val outcome = source.fetchUnnotified(instance)

        assertEquals(listOf(21L), (outcome as Outcome.Success).value.toReport.map { it.id })
    }

    @Test
    fun `geladene Kommentare landen im Cache fuer den Deep Link`() = runTest {
        server.enqueue(jsonResponse(comments(listOf(11L to "2026-09-19T10:00:00"))))

        source.fetchUnnotified(instance)

        // Ohne diesen Schritt wäre die Detailansicht nach dem Antippen leer.
        assertEquals(1, dao.stored.value.size)
        assertEquals(11L, dao.stored.value.single().id)
    }

    @Test
    fun `markNotified merkt sich die neueste Kennung`() = runTest {
        server.enqueue(jsonResponse(comments(listOf(11L to "2026-09-19T10:00:00", 15L to "2026-09-19T12:00:00"))))
        val comments = (source.fetchUnnotified(instance) as Outcome.Success).value.all

        source.markNotified(instance, comments)

        assertEquals(15L, dao.syncState(instance.id)?.lastNotifiedCommentId)
        assertEquals(2, dao.notified.value.size)
    }

    @Test
    fun `standardmaessig zaehlt jeder neue Kommentar`() = runTest {
        // Blogs, die automatisch freischalten, haben nie etwas mit Status
        // "hold" - eine Pruefung nur darauf waere dort wirkungslos.
        server.enqueue(jsonResponse("[]"))

        source.fetchUnnotified(instance)

        val request = server.takeRequest()
        assertEquals("all", request.url.queryParameter("status"))
        assertEquals("edit", request.url.queryParameter("context"))
    }

    @Test
    fun `eine aeltere Plugin-Fassung bricht die Pruefung nicht ab`() = runTest {
        // Fassungen vor 1.3.0 kennen latest_any_comment_id nicht und melden 0.
        // Dann darf die Abkuerzung nicht greifen, sonst kaeme nie etwas an.
        dao.upsertSyncState(
            SyncStateEntity(
                instanceId = instance.id,
                lastSyncEpochMillis = null,
                lastNotifiedCommentId = 98,
                lastNotifiedDateEpochMillis = 0,
            ),
        )
        server.enqueue(
            jsonResponse("""{"pending_count":1,"latest_comment_id":98,"plugin_version":"1.2.0"}"""),
        )
        server.enqueue(jsonResponse("[]"))

        source.fetchUnnotified(testInstance(hasBridgePlugin = true))

        server.takeRequest()
        assertEquals("all", server.takeRequest().url.queryParameter("status"))
    }

    @Test
    fun `jede Stufe fragt den passenden Status ab`() = runTest {
        // "all" heisst bei WordPress genehmigt und offen - Spam und
        // Papierkorb braucht "any". Die Benennung der API ist irrefuehrend,
        // deshalb steht sie hier ausdruecklich fest.
        val erwartet = mapOf(
            NotifyScope.PENDING to "hold",
            NotifyScope.NEW_COMMENTS to "all",
            NotifyScope.EVERYTHING to "any",
        )

        erwartet.forEach { (scope, status) ->
            settings.setSite(
                instance.id,
                settings.siteSettingsValue(instance.id).copy(notifyScope = scope),
            )
            server.enqueue(jsonResponse("[]"))

            source.fetchUnnotified(instance)

            assertEquals("$scope", status, server.takeRequest().url.queryParameter("status"))
        }
    }

    @Test
    fun `mit Plugin wird zuerst der guenstige Statusendpunkt befragt`() = runTest {
        dao.upsertSyncState(
            SyncStateEntity(
                instanceId = instance.id,
                lastSyncEpochMillis = null,
                lastNotifiedCommentId = 98,
                lastNotifiedDateEpochMillis = 0,
            ),
        )
        server.enqueue(
            jsonResponse(
                """{"pending_count":1,"latest_comment_id":98,"latest_any_comment_id":98,
                    "plugin_version":"1.3.0"}""",
            ),
        )

        val outcome = source.fetchUnnotified(testInstance(hasBridgePlugin = true))

        // Nichts Neues laut Plugin: Es darf gar keine Kommentarabfrage folgen.
        assertTrue((outcome as Outcome.Success).value.all.isEmpty())
        assertEquals(1, server.requestCount)
        assertTrue(server.takeRequest().url.encodedPath.endsWith("/commentator/v1/status"))
    }

    @Test
    fun `faellt bei Pluginfehler auf die Kern-API zurueck`() = runTest {
        server.enqueue(MockResponse.Builder().code(500).body("{}").build())
        server.enqueue(jsonResponse(comments(listOf(11L to "2026-09-19T10:00:00"))))

        val outcome = source.fetchUnnotified(testInstance(hasBridgePlugin = true))

        assertEquals(listOf(11L), (outcome as Outcome.Success).value.toReport.map { it.id })
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `eine stummgeschaltete Rolle wird erkannt, aber nicht gemeldet`() = runTest {
        // Erkannt werden muss sie trotzdem: Der Hintergrunddienst vermerkt
        // auch das Stumme, sonst holte es jeder Lauf erneut vom Blog.
        teamRepo.team = Team(members = mapOf(7L to TeamRole("editor", "Redakteur")))
        settings.setSite(
            instance.id,
            settings.siteSettingsValue(instance.id).copy(
                roleStyles = RoleStyles.DEFAULT.with(
                    "editor",
                    RoleStyles.defaultFor("editor").copy(notify = false),
                ),
            ),
        )
        server.enqueue(
            jsonResponse(
                """[{"id":11,"post":1,"parent":0,"author":7,"author_name":"Redaktion",
                 "date_gmt":"2026-09-19T10:00:00","content":{"rendered":"<p>Text</p>"},
                 "status":"hold"}]""",
            ),
        )

        val gefunden = (source.fetchUnnotified(instance) as Outcome.Success).value

        assertTrue(gefunden.toReport.isEmpty())
        assertEquals(listOf(11L), gefunden.muted.map { it.id })
    }

    @Test
    fun `eine meldende Rolle bleibt in der Meldung`() = runTest {
        teamRepo.team = Team(members = mapOf(7L to TeamRole("editor", "Redakteur")))
        server.enqueue(
            jsonResponse(
                """[{"id":11,"post":1,"parent":0,"author":7,"author_name":"Redaktion",
                 "date_gmt":"2026-09-19T10:00:00","content":{"rendered":"<p>Text</p>"},
                 "status":"hold"}]""",
            ),
        )

        val gefunden = (source.fetchUnnotified(instance) as Outcome.Success).value

        assertEquals(listOf(11L), gefunden.toReport.map { it.id })
        assertTrue(gefunden.muted.isEmpty())
    }

    private fun comments(entries: List<Pair<Long, String>>): String =
        entries.joinToString(prefix = "[", postfix = "]") { (id, date) ->
            """
            {"id":$id,"post":1,"parent":0,"author_name":"Max Mustermann",
             "date_gmt":"$date","content":{"rendered":"<p>Text</p>"},"status":"hold"}
            """.trimIndent()
        }

    private fun jsonResponse(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()
}
