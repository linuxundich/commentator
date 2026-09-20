package de.christophlangner.commentator.ui

import app.cash.turbine.test
import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.domain.model.CommentFilter
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.model.ModerationAction
import de.christophlangner.commentator.domain.usecase.ModerateCommentUseCase
import de.christophlangner.commentator.domain.usecase.UndoModerationUseCase
import de.christophlangner.commentator.fake.FakeAuthRepository
import de.christophlangner.commentator.fake.FakeCommentRepository
import de.christophlangner.commentator.fake.FakeConnectivityObserver
import de.christophlangner.commentator.fake.FakeSettingsRepository
import de.christophlangner.commentator.fake.MainDispatcherRule
import de.christophlangner.commentator.fake.testComment
import de.christophlangner.commentator.fake.testInstance
import de.christophlangner.commentator.ui.inbox.InboxViewModel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class InboxViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val comments = FakeCommentRepository()
    private val auth = FakeAuthRepository()
    private val settings = FakeSettingsRepository()
    private val connectivity = FakeConnectivityObserver()

    private fun viewModel() = InboxViewModel(
        authRepository = auth,
        commentRepository = comments,
        moderateComment = ModerateCommentUseCase(comments, connectivity),
        undoModeration = UndoModerationUseCase(comments, connectivity),
        settingsRepository = settings,
        connectivity = connectivity,
    )

    @Test
    fun `startet mit dem Filter fuer offene Kommentare`() = runTest {
        comments.comments.value = listOf(
            testComment(1, status = CommentStatus.PENDING),
            testComment(2, status = CommentStatus.APPROVED),
        )

        viewModel().state.test {
            skipItems(1)
            val state = awaitItem()
            assertEquals(CommentFilter.PENDING, state.filter)
            assertEquals(listOf(1L), state.comments.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Filterwechsel aendert die angezeigte Liste`() = runTest {
        comments.comments.value = listOf(
            testComment(1, status = CommentStatus.PENDING),
            testComment(2, status = CommentStatus.SPAM),
        )
        val model = viewModel()

        model.state.test {
            skipItems(1)
            awaitItem()

            model.setFilter(CommentFilter.SPAM)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(CommentFilter.SPAM, state.filter)
            assertEquals(listOf(2L), state.comments.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `aktualisiert beim Start automatisch`() = runTest {
        val model = viewModel()

        model.state.test {
            skipItems(1)
            awaitItem()
            advanceUntilIdle()
            assertTrue(comments.refreshCount >= 1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Fehler beim Aktualisieren landet im Zustand statt in einer Ausnahme`() = runTest {
        comments.refreshResult = Outcome.Failure(AppError.NoConnection)
        val model = viewModel()

        model.state.test {
            // Alle bis dahin aufgelaufenen Zustände verwerfen und den letzten
            // betrachten: Der Startlauf erzeugt mehrere Zwischenzustände.
            advanceUntilIdle()
            assertEquals(AppError.NoConnection, expectMostRecentItem().error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Moderation meldet den vorherigen Status fuer Rueckgaengig`() = runTest {
        val comment = testComment(1, status = CommentStatus.PENDING)
        comments.comments.value = listOf(comment)
        val model = viewModel()
        advanceUntilIdle()

        model.events.test {
            model.moderate(comment, ModerationAction.MarkAsSpam)
            advanceUntilIdle()

            val event = awaitItem() as InboxViewModel.Event.ModerationDone
            assertEquals(1L, event.commentId)
            assertEquals(CommentStatus.PENDING, event.previousStatus)
            assertTrue(event.undoable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Moderation offline meldet einen verstaendlichen Fehler`() = runTest {
        val comment = testComment(1)
        comments.comments.value = listOf(comment)
        connectivity.online.value = false
        val model = viewModel()
        advanceUntilIdle()

        model.events.test {
            model.moderate(comment, ModerationAction.Approve)
            advanceUntilIdle()

            val event = awaitItem() as InboxViewModel.Event.Failed
            assertEquals(AppError.OfflineWriteBlocked, event.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `offline sperrt die Moderation in der Oberflaeche`() = runTest {
        connectivity.online.value = false
        val model = viewModel()

        model.state.test {
            skipItems(1)
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state.isOffline)
            assertFalse(state.moderationEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ohne Moderationsrecht bleiben Aktionen gesperrt`() = runTest {
        auth.activeInstance.value = testInstance(canModerate = false)
        val model = viewModel()

        model.state.test {
            skipItems(1)
            advanceUntilIdle()
            assertFalse(expectMostRecentItem().moderationEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `abgelehnte Zugangsdaten sperren die Moderation`() = runTest {
        auth.sessionInvalid.value = true
        val model = viewModel()

        model.state.test {
            skipItems(1)
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state.sessionInvalid)
            assertFalse(state.moderationEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Rueckgaengig stellt den Status wieder her`() = runTest {
        val comment = testComment(1, status = CommentStatus.PENDING)
        comments.comments.value = listOf(comment)
        val model = viewModel()
        advanceUntilIdle()

        model.moderate(comment, ModerationAction.MarkAsSpam)
        advanceUntilIdle()
        model.undo(1, CommentStatus.PENDING)
        advanceUntilIdle()

        assertEquals(CommentStatus.PENDING, comments.comments.value.single().status)
    }

    @Test
    fun `naechste Seite wird nur bei vorhandenen weiteren Seiten geladen`() = runTest {
        comments.comments.value = listOf(testComment(1))
        comments.morePages = false
        val model = viewModel()
        advanceUntilIdle()

        model.loadMore()
        advanceUntilIdle()

        // Ohne weitere Seiten darf keine Anfrage entstehen.
        assertFalse(model.state.value.isLoadingMore)
    }
}
