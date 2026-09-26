package de.christophlangner.commentator.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.christophlangner.commentator.domain.model.RoleAccent
import de.christophlangner.commentator.domain.model.RoleStyle
import de.christophlangner.commentator.domain.model.RoleStyles
import de.christophlangner.commentator.domain.model.Team
import de.christophlangner.commentator.domain.model.TeamRole
import de.christophlangner.commentator.ui.settings.RoleDialog
import de.christophlangner.commentator.ui.theme.CommentatorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Der Dialog, in dem eine Rolle eingestellt wird. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "de-rDE-w411dp-h891dp")
class RoleDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val redaktion = TeamRole(Team.EDITOR, "Redakteur")

    private val stile = mutableListOf<RoleStyle>()
    private var teamUmgeschaltet = 0

    private fun render(
        role: TeamRole = redaktion,
        isTeam: Boolean = true,
        canLeaveTeam: Boolean = true,
        style: RoleStyle = RoleStyles.defaultFor(role.slug),
    ) {
        composeRule.setContent {
            CommentatorTheme(dynamicColor = false) {
                RoleDialog(
                    role = role,
                    isTeam = isTeam,
                    canLeaveTeam = canLeaveTeam,
                    style = style,
                    onToggleTeam = { teamUmgeschaltet++ },
                    onStyleChange = { stile += it },
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun `der Dialog nennt alle vier Entscheidungen zur Rolle`() {
        render()

        composeRule.onNodeWithText("Als Team führen").assertIsDisplayed()
        composeRule.onNodeWithText("Eigene Farbe").assertIsDisplayed()
        composeRule.onNodeWithText("In der Liste anzeigen").assertIsDisplayed()
        composeRule.onNodeWithText("Benachrichtigen").assertIsDisplayed()
    }

    @Test
    fun `das Einklappen wird als Aenderung gemeldet`() {
        render()

        composeRule.onNodeWithText("In der Liste anzeigen").performClick()

        assertEquals(false, stile.single().showInTimeline)
    }

    @Test
    fun `die Benachrichtigung laesst sich je Rolle abstellen`() {
        render()

        composeRule.onNodeWithText("Benachrichtigen").performClick()

        assertEquals(false, stile.single().notify)
    }

    @Test
    fun `ein Farbton laesst sich waehlen`() {
        render()

        composeRule.onNodeWithContentDescription("Pflaume").performClick()

        assertEquals(RoleAccent.PFLAUME, stile.single().accent)
    }

    @Test
    fun `ohne Farbe entfaellt die Auswahl der Toene`() {
        // Eine Farbauswahl ueber einer abgeschalteten Farbe waere eine
        // Einstellung ohne Wirkung.
        render(style = RoleStyles.defaultFor(Team.EDITOR).copy(colorEnabled = false))

        assertTrue(
            composeRule.onAllNodesWithContentDescription("Pflaume")
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun `wer nicht zum Team gehoert, hat nichts einzustellen`() {
        // Kommentare ausserhalb des Teams sind genau die, um die es beim
        // Moderieren geht - sie einzuklappen oder stummzuschalten waere die
        // App gegen ihren Zweck gestellt.
        render(isTeam = false)

        composeRule.onNodeWithText("In der Liste anzeigen").assertIsNotEnabled()
        composeRule.onNodeWithText("Benachrichtigen").assertIsNotEnabled()
    }

    @Test
    fun `das eigene Konto laesst sich nicht aus dem Team nehmen`() {
        render(
            role = TeamRole(Team.SELF, "Eigenes Konto"),
            canLeaveTeam = false,
            style = RoleStyles.defaultFor(Team.SELF),
        )

        composeRule.onNodeWithText("Das eigene Konto zählt immer zum Team.").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithText("Als Team führen").fetchSemanticsNodes().isEmpty(),
        )
        // Alles Weitere bleibt trotzdem einstellbar.
        composeRule.onNodeWithText("Benachrichtigen").performClick()
        assertEquals(false, stile.single().notify)
    }
}
