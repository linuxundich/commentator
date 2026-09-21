package de.christophlangner.commentator.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import de.christophlangner.commentator.domain.model.CommentFilter
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.model.ThreadEntry
import de.christophlangner.commentator.fake.testComment
import de.christophlangner.commentator.fake.testInstance
import de.christophlangner.commentator.ui.inbox.InboxScreenContent
import de.christophlangner.commentator.ui.inbox.InboxUiState
import de.christophlangner.commentator.ui.theme.CommentatorTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Wie der Gesprächsfaden in der Liste aussieht. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "de-rDE-w411dp-h891dp")
class InboxFadenOberflaecheTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val bezug = testComment(
        1,
        status = CommentStatus.APPROVED,
        author = "Erika Beispiel",
        content = "Die Frage davor",
    )
    private val antwort = testComment(
        2,
        parentId = 1,
        status = CommentStatus.PENDING,
        author = "Max Mustermann",
        content = "Die offene Antwort",
    )

    private fun render() {
        composeRule.setContent {
            CommentatorTheme(dynamicColor = false) {
                InboxScreenContent(
                    state = InboxUiState(
                        instance = testInstance(),
                        filter = CommentFilter.PENDING,
                        comments = listOf(antwort),
                        threadEntries = listOf(
                            ThreadEntry(bezug, depth = 0, isContext = true),
                            ThreadEntry(antwort, depth = 1),
                        ),
                        isInitialLoad = false,
                    ),
                    snackbarHostState = SnackbarHostState(),
                    onRefresh = {},
                    onEmptyRequest = {},
                    onFilterSelected = {},
                    onOpenComment = {},
                    onModerate = { _, _ -> },
                    onLoadMore = {},
                    onOpenSettings = {},
                    onReauthenticate = {},
                )
            }
        }
    }

    @Test
    fun `der Kommentar davor steht mit in der Liste`() {
        render()

        composeRule.onNodeWithText("Die Frage davor").assertIsDisplayed()
        composeRule.onNodeWithText("Die offene Antwort").assertIsDisplayed()
    }

    @Test
    fun `am Zusammenhang wird nicht moderiert`() {
        // Nur die Antwort gehoert zum Filter; der Kommentar davor steht nur
        // da, damit sie nicht ohne die Frage dasteht.
        render()

        assertEquals(
            1,
            composeRule.onAllNodesWithText("Genehmigen").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `die Antwort steht eingerueckt`() {
        render()

        val bezugLinks = composeRule.onNodeWithText("Die Frage davor")
            .fetchSemanticsNode().boundsInRoot.left
        val antwortLinks = composeRule.onNodeWithText("Die offene Antwort")
            .fetchSemanticsNode().boundsInRoot.left

        assert(antwortLinks > bezugLinks) {
            "Die Antwort muss weiter rechts beginnen: $antwortLinks gegen $bezugLinks"
        }
    }
}
