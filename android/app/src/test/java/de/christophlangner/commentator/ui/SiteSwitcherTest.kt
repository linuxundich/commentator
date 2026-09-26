package de.christophlangner.commentator.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.christophlangner.commentator.fake.testInstance
import de.christophlangner.commentator.ui.inbox.BlogTitleSwitcher
import de.christophlangner.commentator.ui.theme.CommentatorTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Der Blogname in der Kopfleiste.
 *
 * Er ist nur dann ein Umschalter, wenn es etwas umzuschalten gibt - ein Pfeil
 * über einer Liste mit einem Eintrag wäre ein Versprechen ohne Inhalt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "de-rDE-w411dp-h891dp")
class SiteSwitcherTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var klicks = 0

    private fun render(instanceCount: Int) {
        composeRule.setContent {
            CommentatorTheme(dynamicColor = false) {
                BlogTitleSwitcher(
                    instance = testInstance(),
                    instanceCount = instanceCount,
                    onClick = { klicks++ },
                )
            }
        }
    }

    @Test
    fun `bei einem Blog steht dort nur der Name`() {
        render(instanceCount = 1)

        composeRule.onNodeWithText("Testblog").assertIsDisplayed().assertHasNoClickAction()
    }

    @Test
    fun `bei mehreren Blogs fuehrt der Name zur Auswahl`() {
        render(instanceCount = 2)

        composeRule.onNodeWithText("Testblog").assertHasClickAction().performClick()

        assertEquals(1, klicks)
    }
}
