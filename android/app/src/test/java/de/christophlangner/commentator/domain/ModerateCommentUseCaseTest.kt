package de.christophlangner.commentator.domain

import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.model.ModerationAction
import de.christophlangner.commentator.domain.repository.CommentAlerts
import de.christophlangner.commentator.domain.usecase.ModerateCommentUseCase
import de.christophlangner.commentator.domain.usecase.UndoModerationUseCase
import de.christophlangner.commentator.fake.FakeCommentRepository
import de.christophlangner.commentator.fake.FakeConnectivityObserver
import de.christophlangner.commentator.fake.testComment
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModerateCommentUseCaseTest {

    private val repository = FakeCommentRepository()
    private val connectivity = FakeConnectivityObserver()
    private val moderate = ModerateCommentUseCase(repository, connectivity)
    private val undo = UndoModerationUseCase(repository, connectivity)

    @Test
    fun `ohne Verbindung wird nicht geschrieben`() = runTest {
        repository.comments.value = listOf(testComment(1))
        connectivity.online.value = false

        val outcome = moderate("instance-1", 1, ModerationAction.Approve)

        assertEquals(AppError.OfflineWriteBlocked, (outcome as Outcome.Failure).error)
        // Entscheidend: Es wurde gar nicht erst versucht zu senden.
        assertTrue(repository.moderated.isEmpty())
    }

    @Test
    fun `liefert den vorherigen Status fuer Rueckgaengig`() = runTest {
        repository.comments.value = listOf(testComment(1, status = CommentStatus.PENDING))

        val outcome = moderate("instance-1", 1, ModerationAction.MarkAsSpam)

        val result = (outcome as Outcome.Success).value
        assertEquals(CommentStatus.PENDING, result.previousStatus)
        assertTrue(result.undoable)
    }

    @Test
    fun `endgueltiges Loeschen ist nicht widerrufbar`() = runTest {
        repository.comments.value = listOf(testComment(1))

        val outcome = moderate("instance-1", 1, ModerationAction.Delete(permanent = true))

        assertFalse((outcome as Outcome.Success).value.undoable)
    }

    @Test
    fun `Papierkorb ist widerrufbar`() = runTest {
        repository.comments.value = listOf(testComment(1))

        val outcome = moderate("instance-1", 1, ModerationAction.Delete(permanent = false))

        assertTrue((outcome as Outcome.Success).value.undoable)
    }

    @Test
    fun `unbekannter Kommentar ergibt NotFound`() = runTest {
        val outcome = moderate("instance-1", 42, ModerationAction.Approve)

        assertEquals(AppError.NotFound, (outcome as Outcome.Failure).error)
    }

    @Test
    fun `Rueckgaengig stellt den vorherigen Status wieder her`() = runTest {
        repository.comments.value = listOf(testComment(1, status = CommentStatus.PENDING))
        moderate("instance-1", 1, ModerationAction.MarkAsSpam)
        assertEquals(CommentStatus.SPAM, repository.comments.value.first().status)

        undo("instance-1", 1, CommentStatus.PENDING)

        assertEquals(CommentStatus.PENDING, repository.comments.value.first().status)
        assertEquals(1L to CommentStatus.PENDING, repository.restored.single())
    }

    @Test
    fun `Rueckgaengig ist offline ebenfalls gesperrt`() = runTest {
        connectivity.online.value = false

        val outcome = undo("instance-1", 1, CommentStatus.PENDING)

        assertEquals(AppError.OfflineWriteBlocked, (outcome as Outcome.Failure).error)
        assertTrue(repository.restored.isEmpty())
    }

    @Test
    fun `erfolgreiche Moderation nimmt die Benachrichtigung zurueck`() = runTest {
        val dismissed = mutableListOf<Long>()
        val alerts = object : CommentAlerts {
            override fun dismiss(instanceId: String, commentId: Long) {
                dismissed += commentId
            }
        }
        repository.comments.value = listOf(testComment(3))

        ModerateCommentUseCase(repository, connectivity, alerts)(
            "instance-1",
            3,
            ModerationAction.Approve,
        )

        assertEquals(listOf(3L), dismissed)
    }
}
