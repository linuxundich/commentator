package de.christophlangner.commentator.ui

import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.domain.repository.SiteDiscovery
import de.christophlangner.commentator.fake.FakeAuthRepository
import de.christophlangner.commentator.fake.MainDispatcherRule
import de.christophlangner.commentator.fake.testInstance
import de.christophlangner.commentator.ui.setup.AuthCallbackChannel
import de.christophlangner.commentator.ui.setup.SetupStep
import de.christophlangner.commentator.ui.setup.SetupViewModel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SetupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository(instance = null)
    private val channel = AuthCallbackChannel()

    private fun viewModel() = SetupViewModel(auth, channel)

    @Test
    fun `beginnt bei der Adresseingabe`() {
        assertEquals(SetupStep.EnterUrl, viewModel().state.value.step)
    }

    @Test
    fun `erkannter Autorisierungs-Endpunkt fuehrt zum Browser-Schritt`() = runTest {
        auth.discoveryResult = Outcome.Success(
            SiteDiscovery(
                siteUrl = "https://example.test",
                siteName = "Testblog",
                authorizationEndpoint = "https://example.test/wp-admin/authorize-application.php",
            ),
        )
        val model = viewModel()

        model.onUrlChanged("example.test")
        model.checkSite()
        advanceUntilIdle()

        assertEquals(SetupStep.Authorize, model.state.value.step)
        assertEquals("https://example.test", model.state.value.siteUrlInput)
    }

    @Test
    fun `ohne Autorisierungs-Endpunkt bleibt die manuelle Eingabe`() = runTest {
        auth.discoveryResult = Outcome.Success(
            SiteDiscovery("https://example.test", "Testblog", authorizationEndpoint = null),
        )
        val model = viewModel()

        model.checkSite()
        advanceUntilIdle()

        assertEquals(SetupStep.ManualCredentials, model.state.value.step)
    }

    @Test
    fun `nicht erreichbare Installation wird als Fehler gezeigt`() = runTest {
        auth.discoveryResult = Outcome.Failure(AppError.InvalidSite)
        val model = viewModel()

        model.checkSite()
        advanceUntilIdle()

        assertEquals(AppError.InvalidSite, model.state.value.error)
        assertEquals(SetupStep.EnterUrl, model.state.value.step)
    }

    @Test
    fun `Rueckmeldung aus dem Browser schliesst die Anmeldung ab`() = runTest {
        val model = viewModel()
        advanceUntilIdle()

        channel.submit(
            AuthCallbackChannel.Callback(
                siteUrl = "https://example.test",
                userLogin = "moderator",
                applicationPassword = "abcd efgh ijkl mnop",
            ),
        )
        advanceUntilIdle()

        assertTrue(model.state.value.isDone)
    }

    @Test
    fun `das Application Password wird nach der Anmeldung nicht im Zustand behalten`() = runTest {
        val model = viewModel()
        advanceUntilIdle()

        channel.submit(
            AuthCallbackChannel.Callback("https://example.test", "moderator", "geheim"),
        )
        advanceUntilIdle()

        assertEquals("", model.state.value.applicationPassword)
    }

    @Test
    fun `auch bei fehlgeschlagener Anmeldung bleibt kein Passwort im Zustand`() = runTest {
        auth.signInResult = Outcome.Failure(AppError.Unauthorized)
        val model = viewModel()

        model.onUsernameChanged("moderator")
        model.onApplicationPasswordChanged("falsch")
        model.submitManualCredentials()
        advanceUntilIdle()

        assertEquals(AppError.Unauthorized, model.state.value.error)
        assertEquals("", model.state.value.applicationPassword)
        assertFalse(model.state.value.isDone)
    }

    @Test
    fun `abgelehnte Autorisierung wird gemeldet`() = runTest {
        val model = viewModel()
        advanceUntilIdle()

        channel.submitRejection()
        advanceUntilIdle()

        assertEquals(AppError.Unauthorized, model.state.value.error)
        assertFalse(model.state.value.isBusy)
    }

    @Test
    fun `Konto ohne Moderationsrecht wird eingerichtet aber gekennzeichnet`() = runTest {
        auth.signInResult = Outcome.Success(testInstance(canModerate = false))
        val model = viewModel()

        model.onUsernameChanged("leser")
        model.onApplicationPasswordChanged("geheim")
        model.submitManualCredentials()
        advanceUntilIdle()

        assertTrue(model.state.value.isDone)
        assertTrue(model.state.value.signedInWithoutModerationRights)
    }

    @Test
    fun `Anmeldeknopf bleibt ohne Eingaben gesperrt`() {
        val model = viewModel()

        assertFalse(model.state.value.canSubmitCredentials)

        model.onUsernameChanged("moderator")
        assertFalse(model.state.value.canSubmitCredentials)

        model.onApplicationPasswordChanged("geheim")
        assertTrue(model.state.value.canSubmitCredentials)
    }
}
