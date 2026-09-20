package de.christophlangner.commentator.domain

import de.christophlangner.commentator.domain.model.CommentFilter
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.model.ModerationAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Der Statuswechsel ist die Kernlogik der App. WordPress verwendet für
 * Abfrage und Schreiben unterschiedliche Schreibweisen - genau daran scheitern
 * Integrationen üblicherweise.
 */
class CommentStatusTest {

    @Test
    fun `erkennt die Schreibweisen der API`() {
        assertEquals(CommentStatus.APPROVED, CommentStatus.fromApi("approved"))
        assertEquals(CommentStatus.APPROVED, CommentStatus.fromApi("approve"))
        assertEquals(CommentStatus.APPROVED, CommentStatus.fromApi("1"))
        assertEquals(CommentStatus.PENDING, CommentStatus.fromApi("hold"))
        assertEquals(CommentStatus.PENDING, CommentStatus.fromApi("unapproved"))
        assertEquals(CommentStatus.PENDING, CommentStatus.fromApi("0"))
        assertEquals(CommentStatus.SPAM, CommentStatus.fromApi("spam"))
        assertEquals(CommentStatus.TRASH, CommentStatus.fromApi("trash"))
    }

    @Test
    fun `unbekannter Status gilt als ausstehend`() {
        assertEquals(CommentStatus.PENDING, CommentStatus.fromApi("etwas-neues"))
        assertEquals(CommentStatus.PENDING, CommentStatus.fromApi(null))
    }

    @Test
    fun `Abfragewert und Schreibwert unterscheiden sich wie in WordPress`() {
        assertEquals("approve", CommentStatus.APPROVED.queryValue)
        assertEquals("approved", CommentStatus.APPROVED.writeValue)
        assertEquals("hold", CommentStatus.PENDING.queryValue)
        assertEquals("hold", CommentStatus.PENDING.writeValue)
    }

    @Test
    fun `Filter uebersetzt in Abfrageparameter`() {
        assertEquals("all", CommentFilter.ALL.queryValue)
        assertEquals("hold", CommentFilter.PENDING.queryValue)
        assertEquals("approve", CommentFilter.APPROVED.queryValue)
        assertEquals("spam", CommentFilter.SPAM.queryValue)
        assertEquals("trash", CommentFilter.TRASH.queryValue)
    }

    @Test
    fun `Moderationsaktion kennt ihren Zielstatus`() {
        assertEquals(CommentStatus.APPROVED, ModerationAction.Approve.resultingStatus)
        assertEquals(CommentStatus.PENDING, ModerationAction.Hold.resultingStatus)
        assertEquals(CommentStatus.SPAM, ModerationAction.MarkAsSpam.resultingStatus)
        assertEquals(
            CommentStatus.TRASH,
            ModerationAction.Delete(permanent = false).resultingStatus,
        )
    }

    @Test
    fun `endgueltiges Loeschen hat keinen Folgestatus`() {
        assertNull(ModerationAction.Delete(permanent = true).resultingStatus)
    }
}
