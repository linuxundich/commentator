package de.christophlangner.commentator

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.christophlangner.commentator.domain.model.Comment
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.model.ModerationAction
import de.christophlangner.commentator.ui.detail.CommentDetailBody
import de.christophlangner.commentator.ui.detail.CommentDetailUiState
import de.christophlangner.commentator.ui.theme.CommentatorTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Detailansicht auf einem echten Gerät.
 *
 * Ergänzt die Robolectric-Tests um das, was dort nicht geprüft werden kann:
 * tatsächliche Textmessung und Anordnung. Der eingerückte Antwortfaden hängt
 * an `IntrinsicSize.Min` - misst die Plattform anders als erwartet, fällt es
 * hier auf.
 */
@RunWith(AndroidJUnit4::class)
class CommentDetailInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val moderations = mutableListOf<ModerationAction>()

    private fun comment(
        id: Long,
        author: String,
        text: String,
        parentId: Long = 0,
        status: CommentStatus = CommentStatus.PENDING,
    ) = Comment(
        id = id,
        instanceId = "instance-1",
        postId = 1,
        parentId = parentId,
        authorName = author,
        authorEmail = "wer@example.test",
        authorId = 0,
        authorUrl = null,
        avatarUrl = null,
        contentHtml = "<p>$text</p>",
        contentPlain = text,
        date = Instant.now().minusSeconds(3600),
        status = status,
        postTitle = "Linux auf dem Desktop",
        link = null,
    )

    private fun render(replies: List<Comment>) {
        composeRule.setContent {
            CommentatorTheme(dynamicColor = false) {
                CommentDetailBody(
                    state = CommentDetailUiState(
                        comment = comment(1, "Max Mustermann", "Wie sieht es mit der Akkulaufzeit aus?"),
                        replies = replies,
                        isLoading = false,
                        canModerate = true,
                    ),
                    replyText = "",
                    onReplyTextChange = {},
                    onModerate = { moderations += it },
                    onSendReply = {},
                )
            }
        }
    }

    @Test
    fun zeigtKommentarUndAntwortfaden() {
        render(
            listOf(
                comment(2, "Test-Moderatorin", "Rund zwölf Stunden.", parentId = 1, status = CommentStatus.APPROVED),
            ),
        )

        composeRule.onNodeWithText("Max Mustermann").assertIsDisplayed()
        composeRule.onNodeWithText("Wie sieht es mit der Akkulaufzeit aus?", substring = true)
            .assertIsDisplayed()

        composeRule.onNodeWithText(text(R.string.detail_replies)).assertIsDisplayed()
        composeRule.onNodeWithText("Test-Moderatorin").assertIsDisplayed()
        composeRule.onNodeWithText("Rund zwölf Stunden.").assertIsDisplayed()
    }

    @Test
    fun ohneAntwortenFehltDerAbschnitt() {
        render(emptyList())

        composeRule.onNodeWithText("Max Mustermann").assertIsDisplayed()
        assert(
            composeRule.onAllNodesWithText("Test-Moderatorin").fetchSemanticsNodes().isEmpty(),
        ) { "Ohne Antworten darf kein Faden erscheinen" }
    }

    @Test
    fun moderationsaktionWirdGemeldet() {
        render(emptyList())

        composeRule.onNodeWithText(text(R.string.action_approve)).performClick()

        assertEquals(ModerationAction.Approve, moderations.single())
    }
}
