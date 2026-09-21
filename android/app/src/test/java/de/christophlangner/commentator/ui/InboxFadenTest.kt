package de.christophlangner.commentator.ui

import app.cash.turbine.test
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.usecase.ModerateCommentUseCase
import de.christophlangner.commentator.domain.usecase.UndoModerationUseCase
import de.christophlangner.commentator.fake.FakeAuthRepository
import de.christophlangner.commentator.fake.FakeCommentRepository
import de.christophlangner.commentator.fake.FakeConnectivityObserver
import de.christophlangner.commentator.fake.FakeSettingsRepository
import de.christophlangner.commentator.fake.FakeTeamRepository
import de.christophlangner.commentator.fake.MainDispatcherRule
import de.christophlangner.commentator.fake.testComment
import de.christophlangner.commentator.ui.inbox.InboxViewModel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * Der Gesprächsfaden im Posteingang.
 *
 * Der eigentliche Gewinn steckt im Filter „Offen": Dort stehen die
 * Antworten, während der Kommentar davor meist längst genehmigt ist und
 * deshalb gar nicht in der Liste auftaucht.
 */
class InboxFadenTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val comments = FakeCommentRepository()
    private val auth = FakeAuthRepository()
    private val settings = FakeSettingsRepository()
    private val connectivity = FakeConnectivityObserver()
    private val teamRepo = FakeTeamRepository()

    private fun viewModel() = InboxViewModel(
        authRepository = auth,
        commentRepository = comments,
        moderateComment = ModerateCommentUseCase(comments, connectivity),
        undoModeration = UndoModerationUseCase(comments, connectivity),
        teamRepository = teamRepo,
        settingsRepository = settings,
        connectivity = connectivity,
    )

    private fun zeit(minute: Long) = Instant.ofEpochSecond(1_700_000_000 + minute * 60)

    @Test
    fun `eine offene Antwort steht unter dem genehmigten Kommentar davor`() {
        runTest {
            comments.comments.value = listOf(
                testComment(1, status = CommentStatus.APPROVED, date = zeit(0), content = "Frage"),
                testComment(
                    2,
                    parentId = 1,
                    status = CommentStatus.PENDING,
                    date = zeit(5),
                    content = "Antwort",
                ),
            )

            viewModel().state.test {
                skipItems(1)
                advanceUntilIdle()
                val state = expectMostRecentItem()

                // Die Liste selbst enthaelt nur den offenen Kommentar.
                assertEquals(listOf(2L), state.comments.map { it.id })
                // Angezeigt wird er mit dem Kommentar davor als Zusammenhang.
                assertEquals(listOf(1L, 2L), state.entries.map { it.comment.id })
                assertEquals(listOf(true, false), state.entries.map { it.isContext })
                assertEquals(listOf(0, 1), state.entries.map { it.depth })
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `abgeschaltet bleibt es bei der chronologischen Liste`() {
        runTest {
            settings.state.value = settings.state.value.copy(threadedInbox = false)
            comments.comments.value = listOf(
                testComment(1, status = CommentStatus.PENDING, date = zeit(0)),
                testComment(2, parentId = 1, status = CommentStatus.PENDING, date = zeit(5)),
            )

            viewModel().state.test {
                skipItems(1)
                advanceUntilIdle()
                val state = expectMostRecentItem()

                assertTrue("Ohne Faedelung wird nicht eingerueckt", state.entries.all { it.depth == 0 })
                assertTrue("Und nichts steht nur als Zusammenhang da", state.entries.none { it.isContext })
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `die Trefferliste einer Suche bleibt flach`() {
        runTest {
            // Fremde Kommentare als Zusammenhang dazwischenzuschieben stuende
            // gegen das Gesuchte.
            comments.comments.value = listOf(
                testComment(1, status = CommentStatus.PENDING, date = zeit(0), content = "Frage"),
                testComment(
                    2,
                    parentId = 1,
                    status = CommentStatus.PENDING,
                    date = zeit(5),
                    content = "Antwort",
                ),
            )
            val vm = viewModel()

            vm.state.test {
                skipItems(1)
                vm.openSearch()
                vm.setSearchQuery("Antwort")
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertEquals(listOf(2L), state.entries.map { it.comment.id })
                assertTrue(state.entries.all { it.depth == 0 })
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
}
