package de.christophlangner.commentator.domain

import de.christophlangner.commentator.domain.model.CommentThreads
import de.christophlangner.commentator.fake.testComment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Der Aufbau der Gesprächsfäden.
 *
 * Die Reihenfolge ist der eigentliche Gegenstand: Fäden untereinander nach
 * dem jüngsten Beitrag absteigend, innerhalb eines Fadens aufsteigend.
 */
class CommentThreadsTest {

    private fun zeit(minute: Long) = Instant.ofEpochSecond(1_700_000_000 + minute * 60)

    @Test
    fun `eine Antwort steht unter ihrem Kommentar`() {
        val eintraege = CommentThreads.build(
            listOf(
                testComment(1, date = zeit(0), content = "Frage"),
                testComment(2, parentId = 1, date = zeit(5), content = "Antwort"),
            ),
        )

        assertEquals(listOf(1L, 2L), eintraege.map { it.comment.id })
        assertEquals(listOf(0, 1), eintraege.map { it.depth })
    }

    @Test
    fun `der Faden mit dem juengsten Beitrag steht oben`() {
        // Nach dem juengsten Beitrag, nicht nach dem Anfang: Ein Faden, in
        // dem gerade etwas passiert, gehoert nach oben.
        val eintraege = CommentThreads.build(
            listOf(
                testComment(1, date = zeit(0), content = "Alter Faden"),
                testComment(2, parentId = 1, date = zeit(30), content = "Gerade eben"),
                testComment(3, date = zeit(10), content = "Neuerer Einzelkommentar"),
            ),
        )

        assertEquals(listOf(1L, 2L, 3L), eintraege.map { it.comment.id })
    }

    @Test
    fun `innerhalb eines Fadens wird aufsteigend gelesen`() {
        val eintraege = CommentThreads.build(
            listOf(
                testComment(1, date = zeit(0)),
                testComment(3, parentId = 1, date = zeit(20)),
                testComment(2, parentId = 1, date = zeit(10)),
            ),
        )

        assertEquals(listOf(1L, 2L, 3L), eintraege.map { it.comment.id })
    }

    @Test
    fun `ein Kommentar ausserhalb des Filters stellt den Zusammenhang her`() {
        // Beim Filter "Offen" ist der Kommentar, auf den geantwortet wurde,
        // meist schon genehmigt - er waere sonst gar nicht da.
        val eintraege = CommentThreads.build(
            comments = listOf(testComment(2, parentId = 1, date = zeit(5))),
            context = listOf(testComment(1, date = zeit(0))),
        )

        assertEquals(listOf(1L, 2L), eintraege.map { it.comment.id })
        assertTrue("Der Anfang steht nur als Zusammenhang da", eintraege[0].isContext)
        assertFalse(eintraege[1].isContext)
    }

    @Test
    fun `ein Zusammenhang ohne Antwort darunter faellt weg`() {
        val eintraege = CommentThreads.build(
            comments = listOf(testComment(2, date = zeit(5))),
            context = listOf(testComment(9, date = zeit(0))),
        )

        assertEquals(listOf(2L), eintraege.map { it.comment.id })
    }

    @Test
    fun `ein Kommentar zaehlt nicht doppelt`() {
        // Beim Filter "Alle" liegt der Anfang in beiden Listen.
        val eintraege = CommentThreads.build(
            comments = listOf(
                testComment(1, date = zeit(0)),
                testComment(2, parentId = 1, date = zeit(5)),
            ),
            context = listOf(testComment(1, date = zeit(0))),
        )

        assertEquals(listOf(1L, 2L), eintraege.map { it.comment.id })
        assertFalse(eintraege[0].isContext)
    }

    @Test
    fun `eine Antwort ohne bekannten Bezug steht fuer sich`() {
        val eintraege = CommentThreads.build(
            listOf(testComment(2, parentId = 99, date = zeit(5))),
        )

        assertEquals(listOf(2L), eintraege.map { it.comment.id })
        assertEquals(listOf(0), eintraege.map { it.depth })
    }

    @Test
    fun `tiefer als drei Stufen wird nicht eingerueckt`() {
        // Sonst bliebe auf einem Telefon keine Breite mehr fuer den Text.
        val eintraege = CommentThreads.build(
            (1L..6L).map { id ->
                testComment(id, parentId = if (id == 1L) 0 else id - 1, date = zeit(id))
            },
        )

        assertEquals(listOf(0, 1, 2, 3, 3, 3), eintraege.map { it.depth })
    }

    @Test
    fun `ein Verweiskreis bringt den Aufbau nicht zum Absturz`() {
        // Ein fehlerhafter Datenbestand darf die Liste nicht sprengen.
        val eintraege = CommentThreads.build(
            listOf(
                testComment(1, parentId = 2, date = zeit(0)),
                testComment(2, parentId = 1, date = zeit(5)),
            ),
        )

        assertTrue("Ein Kreis hat keine Wurzel und ergibt nichts", eintraege.isEmpty())
    }

    @Test
    fun `ohne Kommentare gibt es nichts zu faedeln`() {
        assertTrue(CommentThreads.build(emptyList()).isEmpty())
    }
}
