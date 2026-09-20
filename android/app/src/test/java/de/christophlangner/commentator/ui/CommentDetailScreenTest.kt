package de.christophlangner.commentator.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import de.christophlangner.commentator.domain.model.Comment
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.model.ModerationAction
import de.christophlangner.commentator.fake.testComment
import de.christophlangner.commentator.ui.detail.CommentDetailBody
import de.christophlangner.commentator.ui.detail.CommentDetailUiState
import de.christophlangner.commentator.ui.theme.CommentatorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Robolectric startet sonst mit englischem Gebietsschema und einer
// Bildschirmgröße von 0 x 0. Beides muss festgelegt sein: Die Standardsprache
// der App ist Deutsch, und ohne Fläche gilt in Compose nichts als sichtbar.
@Config(qualifiers = "de-rDE-w411dp-h891dp")
class CommentDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val moderations = mutableListOf<ModerationAction>()
    private var sentReply: String? = null

    private fun render(state: CommentDetailUiState) {
        composeRule.setContent {
            var replyText by remember { mutableStateOf("") }
            CommentatorTheme(dynamicColor = false) {
                CommentDetailBody(
                    state = state,
                    replyText = replyText,
                    onReplyTextChange = { replyText = it },
                    onModerate = { moderations += it },
                    onSendReply = { sentReply = replyText },
                )
            }
        }
    }

    private fun stateWith(
        comment: Comment?,
        replies: List<Comment> = emptyList(),
        showEmail: Boolean = false,
        isOffline: Boolean = false,
        canModerate: Boolean = true,
        isLoading: Boolean = false,
    ) = CommentDetailUiState(
        comment = comment,
        replies = replies,
        isLoading = isLoading,
        showAuthorEmail = showEmail,
        isOffline = isOffline,
        canModerate = canModerate,
    )

    @Test
    fun `zeigt Autor Datum Beitrag und vollstaendigen Text`() {
        render(stateWith(testComment(1, content = "Ein vollstaendiger Kommentartext")))

        composeRule.onNodeWithText("Max Mustermann").assertIsDisplayed()
        // Teilvergleich: Die Detailansicht stellt den Kommentar als HTML dar,
        // und fromHtml hängt an einen Absatz einen Zeilenumbruch an.
        composeRule.onNodeWithText("Ein vollstaendiger Kommentartext", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Linux auf dem Desktop").assertIsDisplayed()
    }

    @Test
    fun `E-Mail-Adresse bleibt ohne ausdrueckliche Einstellung verborgen`() {
        render(stateWith(testComment(1), showEmail = false))

        assertTrue(
            composeRule.onAllNodesWithText("max@example.test").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun `E-Mail-Adresse erscheint wenn sie eingeschaltet ist`() {
        render(stateWith(testComment(1), showEmail = true))

        composeRule.onNodeWithText("max@example.test").assertIsDisplayed()
    }

    @Test
    fun `Moderationsaktion wird gemeldet`() {
        render(stateWith(testComment(1, status = CommentStatus.PENDING)))

        composeRule.onNodeWithText("Genehmigen").performClick()

        assertEquals(ModerationAction.Approve, moderations.single())
    }

    @Test
    fun `bereits genehmigter Kommentar bietet Genehmigen nicht erneut an`() {
        render(stateWith(testComment(1, status = CommentStatus.APPROVED)))

        assertTrue(
            composeRule.onAllNodesWithText("Genehmigen").fetchSemanticsNodes().isEmpty(),
        )
        composeRule.onNodeWithText("Zurückstellen").assertIsDisplayed()
    }

    @Test
    fun `Antwort schreiben und senden`() {
        render(stateWith(testComment(1)))

        composeRule.onNodeWithText("Antwort").performTextInput("Danke für den Hinweis")
        composeRule.onNodeWithText("Antwort senden").performClick()

        assertEquals("Danke für den Hinweis", sentReply)
    }

    @Test
    fun `Senden bleibt bei leerem Feld gesperrt`() {
        render(stateWith(testComment(1)))

        composeRule.onNodeWithText("Antwort senden").assertIsNotEnabled()
    }

    @Test
    fun `offline sind Antwort und Moderation gesperrt`() {
        render(stateWith(testComment(1), isOffline = true))

        composeRule.onNodeWithText("Genehmigen").assertIsNotEnabled()
        composeRule.onNodeWithText(
            "Ohne Verbindung oder ohne Moderationsrecht kann nicht geantwortet werden.",
        ).assertIsDisplayed()
    }

    @Test
    fun `vorhandene Antworten werden angezeigt`() {
        render(
            stateWith(
                comment = testComment(1),
                replies = listOf(
                    testComment(2, author = "Erika Beispiel", content = "Eine Antwort", parentId = 1),
                ),
            ),
        )

        composeRule.onNodeWithText("Antworten").assertIsDisplayed()
        composeRule.onNodeWithText("Eine Antwort").assertIsDisplayed()
    }

    @Test
    fun `fehlender Kommentar wird erklaert statt leer zu bleiben`() {
        render(stateWith(comment = null, isLoading = false))

        composeRule.onNodeWithText("Dieser Kommentar ist nicht verfügbar.").assertIsDisplayed()
    }
}
