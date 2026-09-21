package de.christophlangner.commentator

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.christophlangner.commentator.domain.model.Comment
import de.christophlangner.commentator.domain.model.CommentFilter
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.model.ModerationAction
import de.christophlangner.commentator.domain.model.WordPressInstance
import de.christophlangner.commentator.ui.inbox.InboxScreenContent
import de.christophlangner.commentator.ui.inbox.InboxUiState
import de.christophlangner.commentator.ui.theme.CommentatorTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Dieselben Kernaussagen wie im JVM-Test, hier aber auf einem echten Gerät.
 *
 * Absichtlich knapp gehalten: Die ausführliche Abdeckung liegt in den
 * Robolectric-Tests, die ohne Gerät laufen. Dieser Test sichert ab, dass die
 * Oberfläche auch auf echter Android-Laufzeit funktioniert.
 */
@RunWith(AndroidJUnit4::class)
class InboxScreenInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val moderations = mutableListOf<ModerationAction>()

    private val comment = Comment(
        id = 1,
        instanceId = "instance-1",
        postId = 1,
        parentId = 0,
        authorName = "Max Mustermann",
        authorEmail = null,
        authorId = 0,
        authorUrl = null,
        avatarUrl = null,
        contentHtml = "<p>Sehr interessanter Artikel</p>",
        contentPlain = "Sehr interessanter Artikel",
        date = Instant.now(),
        status = CommentStatus.PENDING,
        postTitle = "Linux auf dem Desktop",
        link = null,
    )

    private fun render() {
        composeRule.setContent {
            CommentatorTheme(dynamicColor = false) {
                InboxScreenContent(
                    state = InboxUiState(
                        instance = WordPressInstance(
                            id = "instance-1",
                            displayName = "Testblog",
                            siteUrl = "https://example.test",
                            username = "moderator",
                            userId = 2,
                            canModerate = true,
                            hasBridgePlugin = false,
                        ),
                        filter = CommentFilter.PENDING,
                        comments = listOf(comment),
                        isInitialLoad = false,
                    ),
                    snackbarHostState = SnackbarHostState(),
                    onRefresh = {},
                    onFilterSelected = {},
                    onEmptyRequest = {},
                    onOpenComment = {},
                    onModerate = { _, action -> moderations += action },
                    onLoadMore = {},
                    onOpenSettings = {},
                    onReauthenticate = {},
                )
            }
        }
    }

    @Test
    fun zeigtKommentarUndLoestModerationAus() {
        render()

        composeRule.onNodeWithText("Max Mustermann").assertIsDisplayed()
        composeRule.onNodeWithText("Sehr interessanter Artikel").assertIsDisplayed()

        composeRule.onNodeWithText("Genehmigen").performClick()

        assertEquals(ModerationAction.Approve, moderations.single())
    }
}
