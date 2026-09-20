package de.christophlangner.commentator.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.data.account.InstanceStore
import de.christophlangner.commentator.data.remote.ApiExecutor
import de.christophlangner.commentator.data.remote.WordPressApi
import de.christophlangner.commentator.data.remote.WordPressApiProvider
import de.christophlangner.commentator.domain.model.CommentFilter
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.model.ModerationAction
import de.christophlangner.commentator.fake.FakeCommentDao
import de.christophlangner.commentator.fake.testInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.rules.TemporaryFolder
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File

/**
 * Prüft das Zusammenspiel von API und Cache: Was der Server liefert, muss in
 * der Datenbank landen, und was dort verschwindet, darf die Oberfläche nicht
 * weiter anzeigen.
 */
// Robolectric, weil das Mapping die Android-Plattform für die Umwandlung
// von HTML in reinen Text verwendet.
@RunWith(RobolectricTestRunner::class)
class DefaultCommentRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var repository: DefaultCommentRepository
    private lateinit var dataStore: DataStore<Preferences>
    private val dao = FakeCommentDao()
    private val instance = testInstance()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Before
    fun setUp() = runTest {
        server = MockWebServer()
        server.start()

        // Ein echter Scope und eine noch nicht existierende Datei: Ein leerer
        // Datensatz gilt für DataStore als beschädigt, und ein nicht
        // fortgeschaltener Testdispatcher würde die Lesevorgänge blockieren.
        dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { File(temporaryFolder.root, "settings.preferences_pb") },
        )
        val instanceStore = InstanceStore(dataStore)
        instanceStore.upsert(instance)

        val api = Retrofit.Builder()
            .baseUrl(server.url("/wp-json/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(WordPressApi::class.java)

        repository = DefaultCommentRepository(
            dao = dao,
            clientFactory = WordPressApiProvider { _, _ -> api },
            instanceStore = instanceStore,
            executor = ApiExecutor(json),
        )
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `Aktualisieren legt Kommentare und Beitragstitel im Cache ab`() = runTest {
        server.enqueue(listResponse(comments(listOf(1L, 2L)), totalPages = 1))
        server.enqueue(jsonResponse("""[{"id":1,"title":{"rendered":"Linux auf dem Desktop"}}]"""))

        val outcome = repository.refresh(instance.id, CommentFilter.PENDING)

        assertTrue(outcome is Outcome.Success)
        val cached = repository.observeComments(instance.id, CommentFilter.PENDING).first()
        assertEquals(listOf(2L, 1L), cached.map { it.id })
        assertEquals("Linux auf dem Desktop", cached.first().postTitle)
    }

    @Test
    fun `Paginierung folgt den Kopfzeilen`() = runTest {
        server.enqueue(listResponse(comments(listOf(1L)), totalPages = 2))
        server.enqueue(jsonResponse("[]"))
        server.enqueue(jsonResponse("[]"))
        repository.refresh(instance.id, CommentFilter.PENDING)

        assertTrue(repository.hasMorePages(instance.id, CommentFilter.PENDING))

        server.enqueue(listResponse(comments(listOf(2L)), totalPages = 2))
        server.enqueue(jsonResponse("[]"))
        server.enqueue(jsonResponse("[]"))
        val more = repository.loadNextPage(instance.id, CommentFilter.PENDING)

        assertEquals(false, (more as Outcome.Success).value)
        assertFalse(repository.hasMorePages(instance.id, CommentFilter.PENDING))
        assertEquals(
            2,
            repository.observeComments(instance.id, CommentFilter.PENDING).first().size,
        )
    }

    @Test
    fun `serverseitig verschwundene Kommentare fallen aus dem Cache`() = runTest {
        server.enqueue(listResponse(comments(listOf(1L, 2L)), totalPages = 1))
        server.enqueue(jsonResponse("[]"))
        server.enqueue(jsonResponse("[]"))
        repository.refresh(instance.id, CommentFilter.PENDING)
        assertEquals(2, repository.observeComments(instance.id, CommentFilter.PENDING).first().size)

        // Kommentar 2 wurde anderswo moderiert und taucht nicht mehr auf.
        server.enqueue(listResponse(comments(listOf(1L)), totalPages = 1))
        server.enqueue(jsonResponse("[]"))
        server.enqueue(jsonResponse("[]"))
        repository.refresh(instance.id, CommentFilter.PENDING)

        assertEquals(
            listOf(1L),
            repository.observeComments(instance.id, CommentFilter.PENDING).first().map { it.id },
        )
    }

    @Test
    fun `Genehmigen sendet den Schreibwert und aktualisiert den Cache`() = runTest {
        server.enqueue(listResponse(comments(listOf(1L)), totalPages = 1))
        server.enqueue(jsonResponse("[]"))
        server.enqueue(jsonResponse("[]"))
        repository.refresh(instance.id, CommentFilter.PENDING)
        server.takeRequest(); server.takeRequest(); server.takeRequest()

        server.enqueue(jsonResponse(comment(1L, status = "approved")))
        val outcome = repository.moderate(instance.id, 1, ModerationAction.Approve)

        assertTrue(outcome is Outcome.Success)
        assertEquals("""{"status":"approved"}""", server.takeRequest().body?.utf8())
        assertEquals(
            CommentStatus.APPROVED,
            repository.observeComment(instance.id, 1).first()?.status,
        )
    }

    @Test
    fun `Papierkorb setzt den Status statt zu entfernen`() = runTest {
        server.enqueue(listResponse(comments(listOf(1L)), totalPages = 1))
        server.enqueue(jsonResponse("[]"))
        server.enqueue(jsonResponse("[]"))
        repository.refresh(instance.id, CommentFilter.PENDING)

        server.enqueue(jsonResponse("{}"))
        repository.moderate(instance.id, 1, ModerationAction.Delete(permanent = false))

        assertEquals(
            CommentStatus.TRASH,
            repository.observeComment(instance.id, 1).first()?.status,
        )
    }

    @Test
    fun `endgueltiges Loeschen entfernt den Eintrag`() = runTest {
        server.enqueue(listResponse(comments(listOf(1L)), totalPages = 1))
        server.enqueue(jsonResponse("[]"))
        server.enqueue(jsonResponse("[]"))
        repository.refresh(instance.id, CommentFilter.PENDING)

        server.enqueue(jsonResponse("{}"))
        repository.moderate(instance.id, 1, ModerationAction.Delete(permanent = true))

        assertEquals(null, repository.observeComment(instance.id, 1).first())
    }

    @Test
    fun `Antwort wird mit Bezug gesendet und landet im Cache`() = runTest {
        server.enqueue(jsonResponse(comment(99L, parent = 1L)))

        val outcome = repository.reply(instance.id, postId = 1, parentId = 1, content = "Danke")

        val created = (outcome as Outcome.Success).value
        assertEquals(1L, created.parentId)

        val body = server.takeRequest().body?.utf8() ?: ""
        assertTrue(body.contains("\"parent\":1"))
        assertTrue(body.contains("\"status\":\"approved\""))
        assertEquals(1, dao.stored.value.count { it.id == 99L })
    }

    @Test
    fun `Einzelabruf holt auch den Antwortfaden`() = runTest {
        server.enqueue(jsonResponse(comment(2L)))
        enqueuePostTitleLookup()
        // Die Antwort ist freigeschaltet, der Kommentar offen: Die gefilterte
        // Liste wuerde sie nie mitbringen.
        server.enqueue(jsonResponse("[${comment(15L, status = "approved", parent = 2L)}]"))
        enqueuePostTitleLookup()

        val outcome = repository.fetchComment(instance.id, 2)

        assertTrue(outcome is Outcome.Success)
        assertEquals(
            listOf(15L),
            repository.observeReplies(instance.id, 2).first().map { it.id },
        )
    }

    @Test
    fun `geloeschte Antworten verschwinden aus dem Faden`() = runTest {
        server.enqueue(jsonResponse(comment(2L)))
        enqueuePostTitleLookup()
        server.enqueue(jsonResponse("[${comment(15L, parent = 2L)}]"))
        enqueuePostTitleLookup()
        repository.fetchComment(instance.id, 2)
        assertEquals(1, repository.observeReplies(instance.id, 2).first().size)

        server.enqueue(jsonResponse(comment(2L)))
        enqueuePostTitleLookup()
        server.enqueue(jsonResponse("[]"))
        repository.fetchComment(instance.id, 2)

        assertTrue(repository.observeReplies(instance.id, 2).first().isEmpty())
    }

    @Test
    fun `scheiternder Antwortabruf laesst den Kommentar stehen`() = runTest {
        server.enqueue(jsonResponse(comment(2L)))
        enqueuePostTitleLookup()
        server.enqueue(MockResponse.Builder().code(500).build())

        val outcome = repository.fetchComment(instance.id, 2)

        assertTrue(outcome is Outcome.Success)
        assertEquals(2L, repository.observeComment(instance.id, 2).first()?.id)
    }

    @Test
    fun `Autorenhistorie kommt aus der Kopfzeile`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-WP-Total", "7")
                .body("[{\"id\":1}]")
                .build(),
        )

        val outcome = repository.countApprovedByAuthor(instance.id, "max@example.test", 2)

        assertEquals(7, (outcome as Outcome.Success).value)

        // Der Kommentar selbst darf sich nicht mitzaehlen, sonst gaelte ein
        // gerade freigeschalteter Erstkommentar als bekannter Autor.
        val query = server.takeRequest().url
        assertEquals("max@example.test", query.queryParameter("author_email"))
        assertEquals("approve", query.queryParameter("status"))
        assertEquals("2", query.queryParameter("exclude"))
        assertEquals("1", query.queryParameter("per_page"))
    }

    @Test
    fun `fehlende Kopfzeile bedeutet keine Treffer`() = runTest {
        server.enqueue(jsonResponse("[]"))

        val outcome = repository.countApprovedByAuthor(instance.id, "neu@example.test", 9)

        assertEquals(0, (outcome as Outcome.Success).value)
    }

    @Test
    fun `unbekannte Instanz fuehrt nicht zum Absturz`() = runTest {
        val outcome = repository.refresh("gibt-es-nicht", CommentFilter.PENDING)

        assertTrue(outcome is Outcome.Failure)
    }

    /** Der Titel wird erst bei den Beitraegen, dann bei den Seiten gesucht. */
    private fun enqueuePostTitleLookup() {
        server.enqueue(jsonResponse("[]"))
        server.enqueue(jsonResponse("[]"))
    }

    private fun comment(id: Long, status: String = "hold", parent: Long = 0) = """
        {"id":$id,"post":1,"parent":$parent,"author_name":"Max Mustermann",
         "date_gmt":"2026-09-1${id % 9}T10:00:00","content":{"rendered":"<p>Text</p>"},
         "status":"$status"}
    """.trimIndent()

    private fun comments(ids: List<Long>) =
        ids.joinToString(prefix = "[", postfix = "]") { comment(it) }

    private fun jsonResponse(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private fun listResponse(body: String, totalPages: Int) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .addHeader("X-WP-TotalPages", totalPages.toString())
        .body(body)
        .build()
}
