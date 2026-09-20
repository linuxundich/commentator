package de.christophlangner.commentator.ui

import app.cash.turbine.test
import androidx.test.core.app.ApplicationProvider
import de.christophlangner.commentator.data.system.AppIconManager
import de.christophlangner.commentator.fake.FakeAuthRepository
import de.christophlangner.commentator.fake.FakeReplyTemplateRepository
import de.christophlangner.commentator.fake.FakeSettingsRepository
import de.christophlangner.commentator.fake.MainDispatcherRule
import de.christophlangner.commentator.fake.testInstance
import de.christophlangner.commentator.domain.repository.AppSettings
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

@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository(testInstance(hasBridgePlugin = true))
    private val settings = FakeSettingsRepository()
    private val replyTemplates = FakeReplyTemplateRepository()

    // Der AppIconManager greift auf den PackageManager zu, deshalb Robolectric
    // statt eines Doppelgängers: Sein eigenes Verhalten prüft AppIconManagerTest.
    private fun viewModel() = SettingsViewModel(
        auth,
        settings,
        AppIconManager(ApplicationProvider.getApplicationContext()),
        replyTemplates,
    )

    @Test
    fun `nachtraeglich installiertes Bridge-Plugin wird bemerkt`() = runTest {
        val auth = FakeAuthRepository(testInstance(hasBridgePlugin = false))
        auth.refreshedInstance = testInstance(hasBridgePlugin = true)

        val model = SettingsViewModel(
            auth,
            settings,
            AppIconManager(ApplicationProvider.getApplicationContext()),
            replyTemplates,
        )
        model.state.test {
            advanceUntilIdle()
            assertTrue(expectMostRecentItem().instance?.hasBridgePlugin == true)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, auth.capabilitiesRefreshes)
    }

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
    fun `Abmelden entfernt die Instanz`() = runTest {
        val model = viewModel()

        // Der Zustand muss beobachtet werden: stateIn liefert ohne Abonnenten
        // nur den Anfangswert - in der App sorgt die Oberfläche dafür.
        model.state.test {
            skipItems(1)
            model.signOut()
            advanceUntilIdle()

            assertTrue(auth.signedOut)
            assertTrue(expectMostRecentItem().isSignedOut)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `angebotene Intervalle beginnen bei der Untergrenze`() {
        assertEquals(
            AppSettings.MIN_SYNC_INTERVAL_MINUTES,
            viewModel().intervalOptions.first(),
        )
    }
}
