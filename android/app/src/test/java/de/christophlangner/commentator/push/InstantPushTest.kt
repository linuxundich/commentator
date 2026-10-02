package de.christophlangner.commentator.push

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.data.account.InstanceStore
import de.christophlangner.commentator.fake.FakeCommentRepository
import de.christophlangner.commentator.fake.FakeSettingsRepository
import de.christophlangner.commentator.fake.testInstance
import de.christophlangner.commentator.ui.common.ErrorTexts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Die Adresse aus der UnifiedPush-App kommt beim Plugin an - und nur, wenn
 * der Benutzer die Sofortmeldung für diesen Blog eingeschaltet hat.
 */
@RunWith(RobolectricTestRunner::class)
class InstantPushTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var settings: FakeSettingsRepository
    private lateinit var repository: FakeCommentRepository
    private lateinit var push: InstantPush

    private val instance = testInstance()

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        settings = FakeSettingsRepository()
        repository = FakeCommentRepository()
        val instanceStore = InstanceStore(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                produceFile = { File(temporaryFolder.root, "settings.preferences_pb") },
            ),
        )
        instanceStore.upsert(instance)
        push = InstantPush(context, settings, repository, instanceStore)
    }

    private suspend fun site() = settings.siteSettings(instance.id).first()

    @Test
    fun `ohne eingeschaltete Sofortmeldung wird nichts hinterlegt`() = runTest {
        push.onNewEndpoint(instance.id, "https://ntfy.example/up1")

        assertTrue(repository.registeredPush.isEmpty())
        assertNull(site().pushEndpoint)
    }

    @Test
    fun `neue Adresse wird beim Plugin hinterlegt und gemerkt`() = runTest {
        settings.setInstantPush(instance.id, true)

        push.onNewEndpoint(instance.id, "https://ntfy.example/up1")

        assertEquals(listOf("https://ntfy.example/up1"), repository.registeredPush)
        assertEquals("https://ntfy.example/up1", site().pushEndpoint)
    }

    @Test
    fun `eine ersetzte Adresse wird beim Plugin zurueckgenommen`() = runTest {
        settings.setInstantPush(instance.id, true)
        push.onNewEndpoint(instance.id, "https://ntfy.example/alt")

        push.onNewEndpoint(instance.id, "https://ntfy.example/neu")

        assertEquals(listOf("https://ntfy.example/alt"), repository.unregisteredPush)
        assertEquals("https://ntfy.example/neu", site().pushEndpoint)
    }

    @Test
    fun `zu altes Plugin wird als solches benannt`() = runTest {
        settings.setInstantPush(instance.id, true)
        repository.registerPushResult = Outcome.Failure(AppError.NotFound)

        push.onNewEndpoint(instance.id, "https://ntfy.example/up1")

        assertEquals(
            AppError.Unknown(ErrorTexts.PUSH_PLUGIN_OUTDATED),
            push.errors.value[instance.id],
        )
        assertNull(site().pushEndpoint)
    }

    @Test
    fun `beendet die UnifiedPush-App die Registrierung, raeumt die App auf`() = runTest {
        settings.setInstantPush(instance.id, true)
        push.onNewEndpoint(instance.id, "https://ntfy.example/up1")

        push.onUnregistered(instance.id)

        assertFalse(site().instantPush)
        assertNull(site().pushEndpoint)
        assertEquals(listOf("https://ntfy.example/up1"), repository.unregisteredPush)
    }
}
