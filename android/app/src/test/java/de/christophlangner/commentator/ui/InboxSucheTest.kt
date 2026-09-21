package de.christophlangner.commentator.ui

import app.cash.turbine.test
import de.christophlangner.commentator.domain.model.CommentFilter
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Die Kommentarsuche.
 *
 * Gesucht wird auf dem Server über den `search`-Parameter der WordPress-API,
 * nicht im Zwischenspeicher: Wer sucht, sucht gerade das, was er nicht vor
 * Augen hat.
 */
class InboxSucheTest {

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

    private fun bestand() {
        comments.comments.value = listOf(
            testComment(1, status = CommentStatus.PENDING, content = "Etwas über Linux"),
            testComment(2, status = CommentStatus.PENDING, content = "Etwas über Kaffee"),
        )
    }

    @Test
    fun `die Trefferliste zeigt nur die gefundenen Kommentare`() = runTest {
        bestand()
        val vm = viewModel()

        vm.state.test {
            skipItems(1)
            vm.openSearch()
            vm.setSearchQuery("Kaffee")
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertTrue(state.searchActive)
            assertEquals(listOf(2L), state.comments.map { it.id })
            assertEquals(listOf("Kaffee" to CommentFilter.PENDING), comments.searches)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unter zwei Zeichen wird nicht gesucht`() = runTest {
        // Ein einzelner Buchstabe trifft fast alles und kostet nur eine
        // Anfrage an den Blog.
        bestand()
        val vm = viewModel()

        vm.state.test {
            skipItems(1)
            vm.openSearch()
            vm.setSearchQuery("K")
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertTrue(comments.searches.isEmpty())
            assertFalse("Ohne Suche gilt nichts als gesucht", state.searchDone)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `mehrere Tastendruecke ergeben eine Anfrage`() = runTest {
        // Ohne Verzoegerung waere jeder Buchstabe eine eigene Anfrage.
        bestand()
        val vm = viewModel()

        vm.state.test {
            skipItems(1)
            vm.openSearch()
            vm.setSearchQuery("Ka")
            vm.setSearchQuery("Kaf")
            vm.setSearchQuery("Kaffee")
            advanceUntilIdle()

            assertEquals(listOf("Kaffee" to CommentFilter.PENDING), comments.searches)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `das Schliessen bringt die gefilterte Liste zurueck`() = runTest {
        bestand()
        val vm = viewModel()

        vm.state.test {
            skipItems(1)
            vm.openSearch()
            vm.setSearchQuery("Kaffee")
            advanceUntilIdle()
            assertEquals(listOf(2L), expectMostRecentItem().comments.map { it.id })

            vm.closeSearch()
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertFalse(state.searchActive)
            assertEquals(listOf(1L, 2L), state.comments.map { it.id }.sorted())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ein Filterwechsel sucht erneut`() = runTest {
        // Die Suche wirkt innerhalb des Filters; bliebe die alte Trefferliste
        // stehen, passte sie nicht mehr zur Leiste darueber.
        bestand()
        val vm = viewModel()

        vm.state.test {
            skipItems(1)
            vm.openSearch()
            vm.setSearchQuery("Kaffee")
            advanceUntilIdle()

            vm.setFilter(CommentFilter.APPROVED)
            advanceUntilIdle()

            assertEquals(
                listOf(
                    "Kaffee" to CommentFilter.PENDING,
                    "Kaffee" to CommentFilter.APPROVED,
                ),
                comments.searches,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `waehrend der Suche wird nicht nachgeladen`() = runTest {
        // Die Trefferliste ist die Antwort auf eine Frage, keine
        // fortlaufende Liste.
        bestand()
        comments.morePages = true
        val vm = viewModel()

        vm.state.test {
            skipItems(1)
            vm.openSearch()
            vm.setSearchQuery("Kaffee")
            advanceUntilIdle()

            assertFalse(expectMostRecentItem().canLoadMore)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
