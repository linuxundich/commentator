package de.christophlangner.commentator.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import de.christophlangner.commentator.domain.model.CommentFilter
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.fake.testComment
import de.christophlangner.commentator.fake.testInstance
import de.christophlangner.commentator.ui.detail.CommentDetailBody
import de.christophlangner.commentator.ui.detail.CommentDetailUiState
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
 * Zugänglichkeit: Beschriftungen und Verhalten bei großer Schrift.
 *
 * Ein Durchgang mit TalkBack lässt sich nicht automatisieren – im Emulator
 * nimmt TalkBack eingespeiste Gesten nicht an. Was sich prüfen lässt, ist das
 * Material, aus dem ein Bildschirmleser seine Ansage baut: Hat jede bedienbare
 * Stelle einen Namen? Und bleibt die Beschriftung lesbar, wenn jemand die
 * Systemschrift auf 200 % stellt?
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "de-rDE-w411dp-h891dp")
class ZugaenglichkeitTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun posteingang(schriftskalierung: Float = 1f) {
        composeRule.setContent {
            CommentatorTheme(dynamicColor = false) {
                MitSchriftskalierung(schriftskalierung) {
                    InboxScreenContent(
                        state = InboxUiState(
                            instance = testInstance(),
                            filter = CommentFilter.PENDING,
                            comments = listOf(
                                testComment(1, status = CommentStatus.PENDING),
                            ),
                            counts = mapOf(
                                CommentFilter.ALL to 7,
                                CommentFilter.PENDING to 1,
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
    }

    private fun detailansicht(schriftskalierung: Float = 1f) {
        composeRule.setContent {
            var antwort by remember { mutableStateOf("") }
            CommentatorTheme(dynamicColor = false) {
                MitSchriftskalierung(schriftskalierung) {
                    CommentDetailBody(
                        state = CommentDetailUiState(
                            comment = testComment(1),
                            canModerate = true,
                        ),
                        replyText = antwort,
                        onReplyTextChange = { antwort = it },
                        onModerate = {},
                        onSendReply = {},
                    )
                }
            }
        }
    }

    @Test
    fun `jede bedienbare Stelle im Posteingang hat einen Namen`() {
        posteingang()

        ohneNamen(composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()).let {
            assertEquals("Unbenannte Schaltflächen: $it", emptyList<String>(), it)
        }
    }

    @Test
    fun `jede bedienbare Stelle in der Detailansicht hat einen Namen`() {
        detailansicht()

        ohneNamen(composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()).let {
            assertEquals("Unbenannte Schaltflächen: $it", emptyList<String>(), it)
        }
    }

    @Test
    fun `die Filtermarke nennt die Zahl ausgeschrieben`() {
        // Auf der Marke steht "Alle · 7". Der Mittelpunkt ergibt vorgelesen
        // nichts, deshalb liegt daneben eine eigene Ansage.
        posteingang()

        val ansagen = composeRule.onAllNodes(hasClickAction())
            .fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }

        assertTrue(
            "Ansage der Filtermarke fehlt, gefunden: $ansagen",
            ansagen.any { it == "Alle, 7 Kommentare" },
        )
    }

    @Test
    fun `die Beschriftung bricht auch bei doppelter Schrift nicht um`() {
        // Zuvor stand dort eine feste Spaltenbreite von 64 dp. Bei 200 %
        // wurde aus "Beitrag" ein "Beitr / ag" - ein Umbruch mitten im Wort.
        detailansicht(schriftskalierung = 2f)

        assertEquals(1, zeilen(composeRule.onNodeWithText("Beitrag")))
    }

    /** Namen aller bedienbaren Stellen, die keinen haben. */
    private fun ohneNamen(knoten: List<SemanticsNode>): List<String> =
        knoten.filter { name(it) == null }.map { it.id.toString() }

    private fun name(knoten: SemanticsNode): String? =
        knoten.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
            ?: knoten.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
            ?: knoten.config.getOrNull(SemanticsActions.OnClick)?.label
            // Zusammengefasste Knoten tragen den Text ihrer Kinder.
            ?: knoten.children.firstNotNullOfOrNull { name(it) }

    /** Wie viele Zeilen der Text tatsächlich belegt. */
    private fun zeilen(knoten: SemanticsNodeInteraction): Int {
        val ergebnis = mutableListOf<TextLayoutResult>()
        knoten.fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(ergebnis)
        return ergebnis.first().lineCount
    }
}

/**
 * Stellt die Schriftskalierung ein, ohne die Pixeldichte zu verändern.
 *
 * So lässt sich die Systemeinstellung „Schriftgröße" nachstellen, ohne dass
 * sich zugleich die Bildschirmgröße ändert.
 */
@androidx.compose.runtime.Composable
private fun MitSchriftskalierung(
    skalierung: Float,
    inhalt: @androidx.compose.runtime.Composable () -> Unit,
) {
    val dichte = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(dichte.density, skalierung),
        content = inhalt,
    )
}
