package de.christophlangner.commentator.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import de.christophlangner.commentator.domain.model.CommentFilter
import de.christophlangner.commentator.domain.repository.AppSettings
import de.christophlangner.commentator.fake.testInstance
import de.christophlangner.commentator.ui.settings.SettingsContent
import de.christophlangner.commentator.ui.settings.SettingsUiState
import de.christophlangner.commentator.ui.theme.CommentatorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Die Einstellungen, die fuer alle Blogs gelten.
 *
 * Was je Blog gilt, steht im [SiteSettingsScreenTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "de-rDE-w411dp-h891dp")
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val intervalOptions = listOf(15, 30, 60, 180, 360)

    private var openedSite: String? = null
    private var addSiteRequested = 0

    private fun render(
        instances: List<de.christophlangner.commentator.domain.model.WordPressInstance> =
            listOf(testInstance()),
        activeInstanceId: String? = "instance-1",
    ) {
        composeRule.setContent {
            CommentatorTheme(dynamicColor = false) {
                SettingsContent(
                    state = SettingsUiState(
                        instances = instances,
                        activeInstanceId = activeInstanceId,
                        settings = AppSettings(
                            notificationsEnabled = true,
                            syncIntervalMinutes = 15,
                            showAvatars = false,
                            showAuthorEmail = false,
                            threadedInbox = true,
                            lastFilter = CommentFilter.PENDING,
                        ),
                    ),
                    intervalOptions = intervalOptions,
                    onNotificationsEnabled = {},
                    onThreadedInbox = {},
                    onSyncInterval = {},
                    onAppIcon = {},
                    onShowAvatars = {},
                    onShowAuthorEmail = {},
                    onOpenSystemNotifications = {},
                    onOpenSite = { openedSite = it },
                    onAddSite = { addSiteRequested++ },
                    onOpenAbout = {},
                )
            }
        }
    }

    @Test
    fun `jeder eingerichtete Blog steht in der Liste`() {
        render(
            instances = listOf(
                testInstance(id = "instance-1"),
                testInstance(id = "instance-2").copy(
                    displayName = "Zweitblog",
                    siteUrl = "https://zweit.test",
                ),
            ),
        )

        composeRule.onNodeWithText("Testblog").assertIsDisplayed()
        composeRule.onNodeWithText("Zweitblog").assertIsDisplayed()
        // Die Adresse gehoert dazu: Zwei Blogs koennen denselben Namen tragen.
        composeRule.onNodeWithText("zweit.test").assertIsDisplayed()
    }

    @Test
    fun `der angezeigte Blog ist als solcher gekennzeichnet`() {
        render(
            instances = listOf(
                testInstance(id = "instance-1"),
                testInstance(id = "instance-2").copy(displayName = "Zweitblog"),
            ),
            activeInstanceId = "instance-2",
        )

        composeRule.onNodeWithText("Angezeigt").assertIsDisplayed()
    }

    @Test
    fun `ein Tippen auf den Blog fuehrt in seine Einstellungen`() {
        render(
            instances = listOf(
                testInstance(id = "instance-1"),
                testInstance(id = "instance-2").copy(displayName = "Zweitblog"),
            ),
        )

        composeRule.onNodeWithText("Zweitblog").performClick()

        assertEquals("instance-2", openedSite)
    }

    @Test
    fun `ein Blog ohne Moderationsrecht sagt das in seiner Zeile`() {
        render(instances = listOf(testInstance(canModerate = false)))

        composeRule.onNodeWithText("Darf nicht moderieren").assertIsDisplayed()
    }

    @Test
    fun `Hinzufuegen ist von hier erreichbar`() {
        render()

        composeRule.onNodeWithText("Weiteren Blog hinzufügen").performScrollTo().performClick()

        assertEquals(1, addSiteRequested)
    }

    @Test
    fun `der Hauptschalter verweist auf die Einstellung je Blog`() {
        // Der Umfang lag frueher unter dem Hauptschalter. Ohne diesen Hinweis
        // sucht man ihn dort weiter.
        render()

        composeRule.onNodeWithText(
            "Ob ein einzelner Blog meldet und worüber, steht in seinen Einstellungen " +
                "oben unter „Blogs“.",
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `jede Intervall-Auswahl bleibt bedienbar breit`() {
        // Der Fehler, der das absicherte: In einer einzeiligen Row blieb für
        // den letzten Chip fast keine Breite übrig. Sein Text brach senkrecht
        // um und riss eine hohe, leere Fläche in den Bildschirm.
        render()

        intervalOptions.forEach { minutes ->
            val node = composeRule.onNodeWithText("$minutes min")
            node.assertIsDisplayed()
            val bounds = node.getUnclippedBoundsInRoot()
            val width = bounds.right - bounds.left
            assertTrue(
                "Chip für $minutes min ist nur $width breit",
                width >= 48.dp,
            )
        }
    }

    @Test
    fun `Intervalle brauchen mehr als eine Zeile und stehen untereinander`() {
        render()

        val first = composeRule.onNodeWithText("15 min").getUnclippedBoundsInRoot()
        val last = composeRule.onNodeWithText("360 min").getUnclippedBoundsInRoot()

        assertTrue("360 min steht nicht unterhalb von 15 min", last.top > first.bottom)
    }
}
