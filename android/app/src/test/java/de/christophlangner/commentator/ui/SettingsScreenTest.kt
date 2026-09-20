package de.christophlangner.commentator.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import de.christophlangner.commentator.domain.repository.AppSettings
import de.christophlangner.commentator.domain.repository.ReplyTemplate
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

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "de-rDE-w411dp-h891dp")
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val intervalOptions = listOf(15, 30, 60, 180, 360)

    private val deleted = mutableListOf<String>()
    private var addRequested = 0

    private fun render(templates: List<ReplyTemplate> = emptyList()) {
        composeRule.setContent {
            CommentatorTheme(dynamicColor = false) {
                SettingsContent(
                    state = SettingsUiState(
                        instance = testInstance(),
                        templates = templates,
                        settings = AppSettings(
                            notificationsEnabled = true,
                            syncIntervalMinutes = 15,
                            showAvatars = false,
                            showAuthorEmail = false,
                        ),
                    ),
                    intervalOptions = intervalOptions,
                    onNotificationsEnabled = {},
                    onSyncInterval = {},
                    onAppIcon = {},
                    onShowAvatars = {},
                    onShowAuthorEmail = {},
                    onOpenSystemNotifications = {},
                    onSignOutRequest = {},
                    onOpenAbout = {},
                    onAddTemplate = { addRequested++ },
                    onEditTemplate = {},
                    onDeleteTemplate = { deleted += it },
                )
            }
        }
    }

    @Test
    fun `ohne Bausteine steht ein Hinweis statt einer leeren Liste`() {
        render()

        composeRule.onNodeWithText("Noch keine Textbausteine angelegt").assertIsDisplayed()
    }

    @Test
    fun `Bausteine werden aufgelistet und lassen sich loeschen`() {
        render(
            listOf(
                ReplyTemplate("t1", "Danke für den Hinweis."),
                ReplyTemplate("t2", "Das schaue ich mir an."),
            ),
        )

        composeRule.onNodeWithText("Danke für den Hinweis.").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Baustein löschen")[0].performClick()

        assertEquals(listOf("t1"), deleted)
    }

    @Test
    fun `an der Obergrenze laesst sich nichts mehr hinzufuegen`() {
        render(List(ReplyTemplate.MAX_TEMPLATES) { ReplyTemplate("t$it", "Baustein $it") })

        composeRule.onNodeWithText("Baustein hinzufügen").assertIsNotEnabled()
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
