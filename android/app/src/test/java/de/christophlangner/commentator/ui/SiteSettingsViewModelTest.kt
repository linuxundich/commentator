package de.christophlangner.commentator.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import de.christophlangner.commentator.domain.model.RoleAccent
import de.christophlangner.commentator.domain.model.RoleStyles
import de.christophlangner.commentator.domain.model.Team
import de.christophlangner.commentator.fake.FakeAuthRepository
import de.christophlangner.commentator.fake.FakeReplyTemplateRepository
import de.christophlangner.commentator.fake.FakeSettingsRepository
import de.christophlangner.commentator.fake.FakeTeamRepository
import de.christophlangner.commentator.fake.MainDispatcherRule
import de.christophlangner.commentator.fake.testInstance
import de.christophlangner.commentator.ui.settings.SiteSettingsViewModel
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
 * Die Einstellungen eines einzelnen Blogs.
 *
 * Welcher gemeint ist, steht in der Route - nicht "der aktive Blog". Genau das
 * prueft hier mehr als ein Test: Aus der Liste laesst sich auch ein Blog
 * oeffnen, der gerade nicht angezeigt wird.
 */
@RunWith(RobolectricTestRunner::class)
class SiteSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private val replyTemplates = FakeReplyTemplateRepository()
    private val team = FakeTeamRepository()

    private fun viewModel(
        instanceId: String = "instance-1",
        auth: FakeAuthRepository = FakeAuthRepository(testInstance(hasBridgePlugin = true)),
    ) = SiteSettingsViewModel(
        SavedStateHandle(mapOf("instanceId" to instanceId)),
        auth,
        settings,
        replyTemplates,
        team,
    )

    @Test
    fun `nachtraeglich installiertes Bridge-Plugin wird bemerkt`() = runTest {
        val auth = FakeAuthRepository(testInstance(hasBridgePlugin = false))
        auth.refreshedInstance = testInstance(hasBridgePlugin = true)

        val model = viewModel(auth = auth)
        model.state.test {
            advanceUntilIdle()
            assertTrue(expectMostRecentItem().instance?.hasBridgePlugin == true)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, auth.capabilitiesRefreshes)
    }

    @Test
    fun `eine geaenderte Rolleneinstellung wird fuer diesen Blog gespeichert`() = runTest {
        val model = viewModel()

        model.setRoleStyle(
            "editor",
            RoleStyles.defaultFor("editor").copy(accent = RoleAccent.PFLAUME, notify = false),
        )
        advanceUntilIdle()

        val stil = settings.siteSettingsValue("instance-1").roleStyles.of("editor")
        assertEquals(RoleAccent.PFLAUME, stil.accent)
        assertFalse(stil.notify)
        // Der andere Blog bleibt bei seiner Voreinstellung.
        assertTrue(settings.siteSettingsValue("instance-2").roleStyles.of("editor").notify)
    }

    @Test
    fun `andere Rollen bleiben von einer Aenderung unberuehrt`() = runTest {
        val model = viewModel()

        model.setRoleStyle(
            "editor",
            RoleStyles.defaultFor("editor").copy(showInTimeline = false),
        )
        advanceUntilIdle()

        assertTrue(
            settings.siteSettingsValue("instance-1").roleStyles.of("administrator").showInTimeline,
        )
    }

    @Test
    fun `dieser Blog laesst sich einzeln stummschalten`() = runTest {
        val model = viewModel()

        model.setNotificationsEnabled(false)
        advanceUntilIdle()

        assertFalse(settings.siteSettingsValue("instance-1").notificationsEnabled)
        // Der Hauptschalter bleibt davon unberuehrt.
        assertTrue(settings.state.value.notificationsEnabled)
    }

    @Test
    fun `eine geaenderte Teamauswahl verwirft nur den Stand dieses Blogs`() = runTest {
        val model = viewModel()

        model.toggleTeamRole(Team.AUTHOR)
        advanceUntilIdle()

        assertTrue(Team.AUTHOR in settings.siteSettingsValue("instance-1").teamRoles)
        assertEquals(listOf("instance-1"), team.invalidatedIds)
    }

    @Test
    fun `Vorlagen werden unter der Kennung dieses Blogs gefuehrt`() = runTest {
        val model = viewModel(instanceId = "instance-2")

        model.addTemplate("Danke für den Hinweis.")
        advanceUntilIdle()

        assertTrue(replyTemplates.angefragteIds.all { it == "instance-2" })
        assertEquals(
            listOf("Danke für den Hinweis."),
            replyTemplates.state.value.map { it.text },
        )
    }

    @Test
    fun `das Entfernen betrifft nur diesen Blog`() = runTest {
        val auth = FakeAuthRepository(
            instance = testInstance(id = "instance-1"),
            instances = listOf(testInstance(id = "instance-1"), testInstance(id = "instance-2")),
        )
        val model = viewModel(instanceId = "instance-2", auth = auth)

        model.state.test {
            skipItems(1)
            model.signOut()
            advanceUntilIdle()

            assertEquals(listOf("instance-2"), auth.signedOutIds)
            val stand = expectMostRecentItem()
            assertTrue(stand.removed)
            // Es war nicht der letzte - der Posteingang bleibt erreichbar.
            assertFalse(stand.wasLast)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `beim letzten Blog wird das auch gemeldet`() = runTest {
        val auth = FakeAuthRepository(testInstance(id = "instance-1"))
        val model = viewModel(auth = auth)

        model.state.test {
            skipItems(1)
            model.signOut()
            advanceUntilIdle()

            val stand = expectMostRecentItem()
            assertTrue(stand.removed)
            assertTrue(stand.wasLast)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ein Blog laesst sich einstellen, ohne der angezeigte zu sein`() = runTest {
        val auth = FakeAuthRepository(
            instance = testInstance(id = "instance-1"),
            instances = listOf(testInstance(id = "instance-1"), testInstance(id = "instance-2")),
        )
        val model = viewModel(instanceId = "instance-2", auth = auth)

        model.state.test {
            advanceUntilIdle()
            assertEquals("instance-2", expectMostRecentItem().instance?.id)
            cancelAndIgnoreRemainingEvents()
        }
        // Der angezeigte Blog hat sich dadurch nicht geaendert.
        assertEquals("instance-1", auth.activeInstance.value?.id)
    }
}
