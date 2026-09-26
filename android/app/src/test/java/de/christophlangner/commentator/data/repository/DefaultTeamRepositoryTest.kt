package de.christophlangner.commentator.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.data.account.InstanceStore
import de.christophlangner.commentator.data.remote.ApiExecutor
import de.christophlangner.commentator.data.remote.WordPressApi
import de.christophlangner.commentator.data.remote.WordPressApiProvider
import de.christophlangner.commentator.fake.FakeCommentDao
import de.christophlangner.commentator.fake.FakeSettingsRepository
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File

/**
 * Das Team wird festgehalten, nicht nur geholt.
 *
 * Daran haengt mehr als die Rollenmarke: Ohne bekanntes Team gilt niemand
 * als Mitglied, eingeklappte Rollen klappen auf und die
 * Hintergrundpruefung meldet ausgerechnet die Rollen, die stummgeschaltet
 * sind.
 */
class DefaultTeamRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var repository: DefaultTeamRepository
    private lateinit var dataStore: DataStore<Preferences>
    private val dao = FakeCommentDao()
    private val settings = FakeSettingsRepository()

    // Mit Plugin: Ohne es bleibt es beim eigenen Konto, und die Rollen des
    // Blogs kaemen nie zur Sprache.
    private val instance = testInstance(hasBridgePlugin = true)

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Before
    fun setUp() = runTest {
        server = MockWebServer()
        server.start()

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

        repository = DefaultTeamRepository(
            clientFactory = WordPressApiProvider { _, _ -> api },
            executor = ApiExecutor(json),
            instanceStore = instanceStore,
            settingsRepository = settings,
            dao = dao,
        )
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `das geholte Team wird festgehalten`() = runTest {
        server.enqueue(teamAntwort())

        repository.team(instance.id)

        val gespeichert = repository.observeTeam(instance.id).first()
        assertEquals("editor", gespeichert.roleOf(7L)?.slug)
        assertEquals("Redakteur", gespeichert.roleOf(7L)?.name)
        // Das eigene Konto gehoert immer dazu - testInstance traegt die 2.
        assertEquals("administrator", gespeichert.roleOf(2L)?.slug)
        assertEquals(
            listOf("administrator", "editor"),
            gespeichert.availableRoles.map { it.slug }.sorted(),
        )
    }

    @Test
    fun `ohne Verbindung gilt der zuletzt bekannte Stand`() = runTest {
        server.enqueue(teamAntwort())
        repository.team(instance.id)

        // Ein zweites Repository auf derselben Datenbank: leerer
        // Arbeitsspeicher, wie nach einem Neustart. Der Stand im Speicher
        // wuerde die Frage sonst gar nicht erst stellen.
        val nachNeustart = neuesRepository()
        server.enqueue(MockResponse.Builder().code(500).build())
        val outcome = nachNeustart.team(instance.id)

        // Kein Fehler, sondern der Stand von vorhin.
        assertEquals("editor", (outcome as Outcome.Success).value.roleOf(7L)?.slug)
    }

    @Test
    fun `ohne gespeicherten Stand bleibt der Fehler ein Fehler`() = runTest {
        server.enqueue(MockResponse.Builder().code(500).build())

        val outcome = repository.team(instance.id)

        // Nichts zu holen und nichts im Speicher: Dann soll die App nicht
        // so tun, als waere das Team leer - das waere eine Aussage, die
        // sie nicht treffen kann.
        assertEquals(true, outcome is Outcome.Failure)
    }

    /** Ein zweites Repository auf derselben Datenbank - wie nach einem Neustart. */
    private fun neuesRepository(): DefaultTeamRepository {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/wp-json/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(WordPressApi::class.java)
        return DefaultTeamRepository(
            clientFactory = WordPressApiProvider { _, _ -> api },
            executor = ApiExecutor(json),
            instanceStore = InstanceStore(dataStore),
            settingsRepository = settings,
            dao = dao,
        )
    }

    private fun teamAntwort() = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(
            """
            {"roles":[{"slug":"administrator","name":"Administrator"},
                      {"slug":"editor","name":"Redakteur"}],
             "members":[{"id":7,"roles":["editor"]},
                        {"id":2,"roles":["administrator"]}]}
            """.trimIndent(),
        )
        .build()
}
