package de.christophlangner.commentator.domain

import de.christophlangner.commentator.domain.model.CommentSignals
import de.christophlangner.commentator.fake.testComment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentSignalsTest {

    @Test
    fun `zaehlt Links im gerenderten HTML`() {
        val html = """<p>Schau mal <a href="https://a.test">hier</a> und
            <a class="x" href='https://b.test'>dort</a>.</p>"""

        assertEquals(2, CommentSignals.linkCountOf(html))
    }

    @Test
    fun `Text ohne Links ergibt null Treffer`() {
        assertEquals(0, CommentSignals.linkCountOf("<p>Danke für den Beitrag.</p>"))
    }

    @Test
    fun `eine nackte Adresse im Text ist noch kein Link`() {
        // WordPress verlinkt nicht jede Adresse automatisch. Gezaehlt wird,
        // was tatsaechlich als Verweis im Markup steht.
        assertEquals(0, CommentSignals.linkCountOf("<p>Siehe https://example.test</p>"))
    }

    @Test
    fun `Anker ohne href zaehlt nicht`() {
        assertEquals(0, CommentSignals.linkCountOf("""<p><a name="oben"></a>Text</p>"""))
    }

    @Test
    fun `gleicher Text unter mehreren Kommentaren faellt auf`() {
        val comments = listOf(
            testComment(1).copy(contentPlain = "Toller Beitrag!"),
            testComment(2).copy(contentPlain = "Etwas ganz anderes"),
            testComment(3).copy(contentPlain = "toller beitrag!"),
        )

        assertEquals(setOf(1L, 3L), CommentSignals.duplicatedIds(comments))
    }

    @Test
    fun `leerer Text gilt nicht als Dublette`() {
        val comments = listOf(
            testComment(1).copy(contentPlain = ""),
            testComment(2).copy(contentPlain = "   "),
        )

        assertTrue(CommentSignals.duplicatedIds(comments).isEmpty())
    }

    @Test
    fun `ohne Auffaelligkeit wird nichts angezeigt`() {
        assertFalse(CommentSignals().hasAny)
        assertFalse(CommentSignals(firstTimeAuthor = false).hasAny)
        assertTrue(CommentSignals(linkCount = 1).hasAny)
        assertTrue(CommentSignals(duplicated = true).hasAny)
        assertTrue(CommentSignals(firstTimeAuthor = true).hasAny)
    }
}
