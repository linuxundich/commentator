package de.christophlangner.commentator.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.domain.model.Comment
import de.christophlangner.commentator.domain.model.CommentFilter
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.model.Team
import de.christophlangner.commentator.domain.model.ModerationAction
import de.christophlangner.commentator.fake.testComment
import de.christophlangner.commentator.fake.testInstance
import de.christophlangner.commentator.ui.inbox.InboxScreenContent
import de.christophlangner.commentator.ui.inbox.InboxUiState
import de.christophlangner.commentator.ui.theme.CommentatorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Oberflächentests des Posteingangs.
 *
 * Geprüft wird der zustandslose Teil: Er bekommt einen Zustand und gibt
 * Ereignisse zurück. Dadurch braucht der Test weder Hilt noch ein Gerät.
 */
@RunWith(RobolectricTestRunner::class)
// Robolectric startet sonst mit englischem Gebietsschema und einer
// Bildschirmgröße von 0 x 0. Beides muss festgelegt sein: Die Standardsprache
// der App ist Deutsch, und ohne Fläche gilt in Compose nichts als sichtbar.
@Config(qualifiers = "de-rDE-w411dp-h891dp")
class InboxScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var selectedFilter: CommentFilter? = null
    private var openedComment: Comment? = null
    private val moderations = mutableListOf<Pair<Comment, ModerationAction>>()
    private var refreshed = 0
    private var emptyRequested = 0

    private fun render(state: InboxUiState) {
        composeRule.setContent {
            CommentatorTheme(dynamicColor = false) {
                InboxScreenContent(
                    state = state,
                    snackbarHostState = SnackbarHostState(),
                    onRefresh = { refreshed++ },
                    onEmptyRequest = { emptyRequested++ },
                    onFilterSelected = { selectedFilter = it },
                    onOpenComment = { openedComment = it },
                    onModerate = { comment, action -> moderations += comment to action },
                    onLoadMore = {},
                    onOpenSettings = {},
                    onReauthenticate = {},
                )
            }
        }
    }

    private fun stateWith(
        comments: List<Comment>,
        isOffline: Boolean = false,
        error: AppError? = null,
        sessionInvalid: Boolean = false,
    ) = InboxUiState(
        instance = testInstance(),
        filter = CommentFilter.PENDING,
        comments = comments,
        isInitialLoad = false,
        isOffline = isOffline,
        sessionInvalid = sessionInvalid,
        error = error,
    )

    @Test
    fun `Kommentare des Teams tragen eine Marke`() {
        render(
            InboxUiState(
                instance = testInstance(),
                comments = listOf(
                    testComment(1, authorId = 2),
                    testComment(2, authorId = 0),
                ),
                team = Team(memberIds = setOf(2L)),
            ),
        )

        // Genau einmal: nur der Kommentar aus dem Team.
        assertEquals(
            1,
            composeRule.onAllNodesWithText("Team").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `ohne ermitteltes Team bleibt die Liste unmarkiert`() {
        render(
            InboxUiState(
                instance = testInstance(),
                comments = listOf(testComment(1, authorId = 2)),
            ),
        )

        assertEquals(0, composeRule.onAllNodesWithText("Team").fetchSemanticsNodes().size)
    }

    @Test
    fun `beim Erstaufbau erscheint nur eine Ladeanzeige`() {
        // Vorher liefen zwei gleichzeitig: die Platzhalter und der Kreis des
        // Herunterziehens.
        render(InboxUiState(instance = testInstance(), isInitialLoad = true, isRefreshing = true))

        assertEquals(
            1,
            composeRule.onAllNodesWithContentDescription("Kommentare werden geladen…")
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `genehmigter Kommentar bietet Antworten als Hauptaktion`() {
        render(stateWith(listOf(testComment(1, status = CommentStatus.APPROVED))))

        // Als Text, nicht als Symbol: Die Hauptaktion ist beschriftet.
        composeRule.onNodeWithText("Antworten").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Genehmigen")
            .fetchSemanticsNodes()
            .let { assertEquals(0, it.size) }
    }

    @Test
    fun `offener Kommentar bietet Genehmigen als Hauptaktion`() {
        render(stateWith(listOf(testComment(1, status = CommentStatus.PENDING))))

        composeRule.onNodeWithText("Genehmigen").assertIsDisplayed()
        // Die uebrigen Aktionen sind Symbole mit gesprochener Beschriftung.
        composeRule.onNodeWithContentDescription("Papierkorb").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Antworten").assertIsDisplayed()
    }

    @Test
    fun `Leeren erscheint nur bei Spam und Papierkorb`() {
        render(
            InboxUiState(
                instance = testInstance(),
                filter = CommentFilter.PENDING,
                counts = mapOf(CommentFilter.PENDING to 3),
            ),
        )

        composeRule.onAllNodesWithContentDescription("Spam leeren")
            .fetchSemanticsNodes()
            .let { assertTrue("Bei Offen darf es das nicht geben", it.isEmpty()) }
    }

    @Test
    fun `leerer Spam-Ordner bekommt keinen Knopf`() {
        // Ein Knopf, der nichts tut, waere irritierend.
        render(
            InboxUiState(
                instance = testInstance(),
                filter = CommentFilter.SPAM,
                counts = mapOf(CommentFilter.SPAM to 0),
            ),
        )

        composeRule.onAllNodesWithContentDescription("Spam leeren")
            .fetchSemanticsNodes()
            .let { assertTrue("Ohne Spam kein Knopf", it.isEmpty()) }
    }

    @Test
    fun `Leeren wird gemeldet statt sofort ausgefuehrt`() {
        render(
            InboxUiState(
                instance = testInstance(),
                filter = CommentFilter.SPAM,
                counts = mapOf(CommentFilter.SPAM to 7),
            ),
        )

        composeRule.onNodeWithContentDescription("Spam leeren").performClick()

        assertEquals(1, emptyRequested)
    }

    @Test
    fun `Filterleiste zeigt die Anzahl je Filter`() {
        render(
            InboxUiState(
                instance = testInstance(),
                comments = listOf(testComment(1, status = CommentStatus.PENDING)),
                counts = mapOf(CommentFilter.PENDING to 3, CommentFilter.SPAM to 12),
            ),
        )

        composeRule.onNodeWithText("Offen · 3").assertIsDisplayed()
        composeRule.onNodeWithText("Spam · 12").assertIsDisplayed()
    }

    @Test
    fun `ohne ermittelte Anzahl steht nur der Name`() {
        // Eine erfundene Null waere schlechter als gar keine Angabe.
        // Ohne Kommentare in der Liste, damit "Spam" nur als Filter vorkommt
        // und nicht zusaetzlich als Moderationsaktion auf einer Karte.
        render(
            InboxUiState(
                instance = testInstance(),
                counts = mapOf(CommentFilter.PENDING to 3),
            ),
        )

        composeRule.onNodeWithText("Offen · 3").assertIsDisplayed()
        composeRule.onNodeWithText("Spam").assertIsDisplayed()
    }

    @Test
    fun `zeigt Autor Text und Beitrag eines Kommentars`() {
        render(stateWith(listOf(testComment(1, content = "Sehr interessanter Artikel"))))

        composeRule.onNodeWithText("Max Mustermann").assertIsDisplayed()
        composeRule.onNodeWithText("Sehr interessanter Artikel").assertIsDisplayed()
        composeRule.onNodeWithText("Beitrag: Linux auf dem Desktop").assertIsDisplayed()
    }

    @Test
    fun `zeigt alle Filter an`() {
        render(stateWith(emptyList()))

        listOf("Alle", "Offen", "Genehmigt", "Spam", "Papierkorb").forEach { label ->
            assertTrue(
                "Filter $label fehlt",
                composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty(),
            )
        }
    }

    @Test
    fun `Filterauswahl wird gemeldet`() {
        render(stateWith(emptyList()))

        composeRule.onAllNodesWithText("Spam")[0].performClick()

        assertEquals(CommentFilter.SPAM, selectedFilter)
    }

    @Test
    fun `Antippen eines Kommentars oeffnet ihn`() {
        val comment = testComment(42, content = "Bitte oeffnen")
        render(stateWith(listOf(comment)))

        composeRule.onNodeWithText("Bitte oeffnen").performClick()

        assertEquals(42L, openedComment?.id)
    }

    @Test
    fun `Genehmigen loest die passende Moderationsaktion aus`() {
        val comment = testComment(7, status = CommentStatus.PENDING)
        render(stateWith(listOf(comment)))

        composeRule.onNodeWithText("Genehmigen").performClick()

        assertEquals(1, moderations.size)
        assertEquals(7L, moderations.first().first.id)
        assertEquals(ModerationAction.Approve, moderations.first().second)
    }

    @Test
    fun `Spam loest die passende Moderationsaktion aus`() {
        render(stateWith(listOf(testComment(7))))

        // Ueber die gesprochene Beschriftung, nicht ueber den Text: Die
        // Kartenaktion ist ein Symbolknopf, "Spam" steht als Text nur noch
        // am Filter.
        composeRule.onNodeWithContentDescription("Spam").performClick()

        assertEquals(ModerationAction.MarkAsSpam, moderations.single().second)
    }

    @Test
    fun `Aktualisieren wird gemeldet`() {
        render(stateWith(listOf(testComment(1))))

        composeRule.onNodeWithContentDescription("Aktualisieren").performClick()

        assertEquals(1, refreshed)
    }

    @Test
    fun `Leerzustand erklaert die Lage statt leer zu bleiben`() {
        render(stateWith(emptyList()))

        composeRule.onNodeWithText("Nichts zu moderieren").assertIsDisplayed()
        composeRule.onNodeWithText("Es warten keine Kommentare auf eine Entscheidung.")
            .assertIsDisplayed()
    }

    @Test
    fun `Fehlerzustand zeigt verstaendlichen Text und Wiederholung`() {
        render(stateWith(emptyList(), error = AppError.NoConnection))

        composeRule.onNodeWithText("Keine Internetverbindung.").assertIsDisplayed()
        composeRule.onNodeWithText("Erneut versuchen").performClick()
        assertEquals(1, refreshed)
    }

    @Test
    fun `offline erscheint ein Hinweis und Aktionen sind gesperrt`() {
        render(stateWith(listOf(testComment(1)), isOffline = true))

        composeRule.onNodeWithText(
            "Offline. Es sind noch keine Kommentare gespeichert.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Genehmigen").assertIsNotEnabled()
    }

    @Test
    fun `abgelehnte Zugangsdaten werden als Band angezeigt`() {
        render(stateWith(listOf(testComment(1)), sessionInvalid = true))

        composeRule.onNodeWithText(
            "Die Zugangsdaten wurden abgelehnt. Moderation ist derzeit nicht möglich.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Neu anmelden").assertIsDisplayed()
    }
}
