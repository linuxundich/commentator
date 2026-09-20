package de.christophlangner.commentator.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.christophlangner.commentator.BuildConfig
import de.christophlangner.commentator.ui.about.AboutContent
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
class AboutScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var repositoryOpened = 0

    private fun render() {
        composeRule.setContent {
            CommentatorTheme(dynamicColor = false) {
                AboutContent(onOpenRepository = { repositoryOpened++ })
            }
        }
    }

    @Test
    fun `zeigt die Version aus dem Build`() {
        render()

        composeRule.onNodeWithText(BuildConfig.VERSION_NAME, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(BuildConfig.VERSION_CODE.toString()).assertIsDisplayed()
    }

    @Test
    fun `nennt den Commit, aus dem der Build entstand`() {
        // Damit eine Fehlermeldung einem Stand zugeordnet werden kann.
        render()

        composeRule.onNodeWithText("Commit").assertIsDisplayed()
        // Nicht auf genau einen Treffer pruefen: Ausserhalb eines Tags steht
        // der Commit zusaetzlich im Versionsnamen.
        assertTrue(
            "Commit wird nicht angezeigt",
            composeRule.onAllNodesWithText(BuildConfig.GIT_COMMIT, substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun `die Version stammt aus Git und ist nicht fest verdrahtet`() {
        assertTrue(
            "Commit-Kennung fehlt: ${BuildConfig.GIT_COMMIT}",
            BuildConfig.GIT_COMMIT.matches(Regex("[0-9a-f]{8}")),
        )
        assertTrue("Buildnummer muss aus der Commit-Anzahl kommen", BuildConfig.VERSION_CODE > 1)
    }

    @Test
    fun `benennt Datenverarbeitung und Lizenz`() {
        render()

        composeRule.onNodeWithText("Daten und Verbindungen").assertIsDisplayed()
        composeRule.onNodeWithText("Lizenz").assertIsDisplayed()
    }

    @Test
    fun `der Quelltext-Verweis wird gemeldet`() {
        render()

        composeRule.onNodeWithText("Quelltext öffnen").performClick()

        assertEquals(1, repositoryOpened)
    }
}
