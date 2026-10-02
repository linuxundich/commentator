package de.christophlangner.commentator.domain

import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.domain.usecase.ReplyToCommentUseCase
import de.christophlangner.commentator.fake.FakeCommentRepository
import de.christophlangner.commentator.fake.FakeConnectivityObserver
import de.christophlangner.commentator.fake.testComment
import kotlinx.coroutines.test.runTest
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.model.ModerationAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyToCommentUseCaseTest {

    private val repository = FakeCommentRepository()
    private val connectivity = FakeConnectivityObserver()
    private val reply = ReplyToCommentUseCase(repository, connectivity)

    @Test
    fun `leere Antwort wird abgelehnt`() = runTest {
        val outcome = reply("instance-1", testComment(1), "   ")

        assertEquals(
            AppError.WordPress("empty_reply", 400),
            (outcome as Outcome.Failure).error,
        )
    }

    @Test
    fun `ohne Verbindung wird nicht gesendet`() = runTest {
        connectivity.online.value = false

        val outcome = reply("instance-1", testComment(1), "Danke für den Hinweis")

        assertEquals(AppError.OfflineWriteBlocked, (outcome as Outcome.Failure).error)
    }

    @Test
    fun `Antwort verweist auf den urspruenglichen Kommentar`() = runTest {
        val parent = testComment(id = 7, postId = 3)

        val outcome = reply("instance-1", parent, "  Danke!  ")

        val created = (outcome as Outcome.Success).value
        assertEquals(7L, created.parentId)
        assertTrue(created.isReply)
    }

    @Test
    fun `Antwort auf einen offenen Kommentar gibt ihn zuerst frei`() = runTest {
        val parent = testComment(id = 7, status = CommentStatus.PENDING)
        repository.comments.value = listOf(parent)

        val outcome = reply("instance-1", parent, "Danke!")

        assertTrue(outcome is Outcome.Success)
        assertEquals(listOf(7L to ModerationAction.Approve), repository.moderated)
        assertEquals(CommentStatus.APPROVED, repository.comments.value.single { it.id == 7L }.status)
    }

    @Test
    fun `scheitert die Freigabe, wird nicht geantwortet`() = runTest {
        val parent = testComment(id = 7, status = CommentStatus.PENDING)
        repository.moderateResult = Outcome.Failure(AppError.Forbidden)
        var replied = false
        repository.replyResult = {
            replied = true
            Outcome.Success(testComment(id = 999, parentId = 7))
        }

        val outcome = reply("instance-1", parent, "Danke!")

        assertEquals(AppError.Forbidden, (outcome as Outcome.Failure).error)
        assertFalse(replied)
    }

    @Test
    fun `Antwort auf einen freigegebenen Kommentar aendert seinen Status nicht`() = runTest {
        val parent = testComment(id = 7, status = CommentStatus.APPROVED)

        reply("instance-1", parent, "Danke!")

        assertTrue(repository.moderated.isEmpty())
    }
}
