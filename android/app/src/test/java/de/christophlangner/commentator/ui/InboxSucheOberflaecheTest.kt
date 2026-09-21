package de.christophlangner.commentator.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import de.christophlangner.commentator.domain.model.CommentFilter
import de.christophlangner.commentator.domain.model.CommentStatus
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

/** Die Suchleiste im Posteingang. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "de-rDE-w411dp-h891dp")
class InboxSucheOberflaecheTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var geoeffnet = 0
    private var geschlossen = 0
    private val eingaben = mutableListOf<String>()

    private fun render(state: InboxUiState) {
        composeRule.setContent {
            CommentatorTheme(dynamicColor = false) {
                InboxScreenContent(
                    state = state,
                    snackbarHostState = SnackbarHostState(),
                    onRefresh = {},
                    onEmptyRequest = {},
                    onFilterSelected = {},
                    onOpenComment = {},
                    onModerate = { _, _ -> },
                    onLoadMore = {},
                    onOpenSettings = {},
                    onReauthenticate = {},
                    onOpenSearch = { geoeffnet++ },
                    onCloseSearch = { geschlossen++ },
                    onSearchQueryChange = { eingaben += it },
                )
            }
        }
    }

    private fun zustand(
        searchActive: Boolean = false,
        searchQuery: String = "",
        searchDone: Boolean = false,
        comments: List<de.christophlangner.commentator.domain.model.Comment> =
            listOf(testComment(1, status = CommentStatus.PENDING)),
    ) = InboxUiState(
        instance = testInstance(),
        filter = CommentFilter.PENDING,
        comments = comments,
        isInitialLoad = false,
        searchActive = searchActive,
        searchQuery = searchQuery,
        searchDone = searchDone,
    )

    @Test
    fun `der Knopf oeffnet die Suche`() {
        render(zustand())

        composeRule.onNodeWithContentDescription("Suchen").performClick()

        assertEquals(1, geoeffnet)
    }

    @Test
    fun `im Suchmodus nimmt das Feld die Eingabe entgegen`() {
        render(zustand(searchActive = true))

        composeRule.onNodeWithText("Kommentare durchsuchen").performTextInput("Kaffee")

        assertEquals(listOf("Kaffee"), eingaben)
    }

    @Test
    fun `im Suchmodus weichen die uebrigen Knoepfe`() {
        // Sonst stuende das Suchfeld zwischen vier Symbolen und waere kaum
        // noch breit genug zum Lesen.
        render(zustand(searchActive = true))

        composeRule.onNodeWithContentDescription("Aktualisieren").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Einstellungen").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Suche schließen").assertIsDisplayed()
    }

    @Test
    fun `ohne Treffer steht dort nicht der Leerzustand des Filters`() {
        render(zustand(searchActive = true, searchQuery = "Kaffee", searchDone = true, comments = emptyList()))

        composeRule.onNodeWithText("Nichts gefunden").assertIsDisplayed()
    }

    @Test
    fun `das Schliessen meldet sich zurueck`() {
        render(zustand(searchActive = true))

        composeRule.onNodeWithContentDescription("Suche schließen").performClick()

        assertEquals(1, geschlossen)
    }
}
