package de.christophlangner.commentator.ui

import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import de.christophlangner.commentator.data.system.AppIconManager
import de.christophlangner.commentator.domain.repository.AppSettings
import de.christophlangner.commentator.fake.FakeAuthRepository
import de.christophlangner.commentator.fake.FakeSettingsRepository
import de.christophlangner.commentator.fake.MainDispatcherRule
import de.christophlangner.commentator.fake.testInstance
import de.christophlangner.commentator.ui.settings.SettingsViewModel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Die Einstellungen, die fuer alle Blogs gelten.
 *
 * Was je Blog gilt, prueft [SiteSettingsViewModelTest].
 */
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository(testInstance(hasBridgePlugin = true))
    private val settings = FakeSettingsRepository()

    // Der AppIconManager greift auf den PackageManager zu, deshalb Robolectric
    // statt eines Doppelgängers: Sein eigenes Verhalten prüft AppIconManagerTest.
    private fun viewModel() = SettingsViewModel(
        auth,
        settings,
        AppIconManager(ApplicationProvider.getApplicationContext()),
    )

    @Test
    fun `Avatare sind standardmaessig aus`() {
        // Datenschutzentscheidung: Avatare erzeugen eine Verbindung zu einem
        // Dritten und werden deshalb nicht ungefragt geladen.
        assertFalse(settings.state.value.showAvatars)
    }

    @Test
    fun `E-Mail-Anzeige ist standardmaessig aus`() {
        assertFalse(settings.state.value.showAuthorEmail)
    }

    @Test
    fun `Einstellungen werden uebernommen`() = runTest {
        val model = viewModel()

        model.setShowAvatars(true)
        model.setShowAuthorEmail(true)
        model.setNotificationsEnabled(false)
        model.setSyncInterval(60)
        advanceUntilIdle()

        assertTrue(settings.state.value.showAvatars)
        assertTrue(settings.state.value.showAuthorEmail)
        assertFalse(settings.state.value.notificationsEnabled)
        assertEquals(60, settings.state.value.syncIntervalMinutes)
    }

    @Test
    fun `angebotene Intervalle beginnen bei der Untergrenze`() {
        assertEquals(
            AppSettings.MIN_SYNC_INTERVAL_MINUTES,
            viewModel().intervalOptions.first(),
        )
    }

    @Test
    fun `alle eingerichteten Blogs stehen im Zustand, der angezeigte gekennzeichnet`() = runTest {
        val zweiter = testInstance(id = "instance-2")
        val auth = FakeAuthRepository(
            instance = testInstance(id = "instance-1"),
            instances = listOf(testInstance(id = "instance-1"), zweiter),
        )
        val model = SettingsViewModel(
            auth,
            settings,
            AppIconManager(ApplicationProvider.getApplicationContext()),
        )

        model.state.test {
            advanceUntilIdle()
            val stand = expectMostRecentItem()
            assertEquals(
                listOf("instance-1", "instance-2"),
                stand.instances.map { it.id },
            )
            assertEquals("instance-1", stand.activeInstanceId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dieser Bildschirm frischt keine Rechte auf`() = runTest {
        // Das gehoert zum jeweiligen Blog und geschieht in seinen eigenen
        // Einstellungen. Hier waere unklar, welcher Blog gemeint ist.
        val model = viewModel()

        model.state.test {
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(0, auth.capabilitiesRefreshes)
    }
}
