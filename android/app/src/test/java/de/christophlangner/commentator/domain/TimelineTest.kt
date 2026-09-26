package de.christophlangner.commentator.domain

import de.christophlangner.commentator.domain.model.RoleStyles
import de.christophlangner.commentator.domain.model.Team
import de.christophlangner.commentator.domain.model.TeamRole
import de.christophlangner.commentator.domain.model.ThreadEntry
import de.christophlangner.commentator.domain.model.Timeline
import de.christophlangner.commentator.domain.model.TimelineRow
import de.christophlangner.commentator.fake.testComment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineTest {

    private val redaktion = TeamRole(Team.EDITOR, "Redakteur")
    private val leitung = TeamRole(Team.ADMINISTRATOR, "Administrator")

    private val team = Team(members = mapOf(7L to redaktion, 8L to leitung))

    /** Rollen, deren Kommentare eingeklappt werden. */
    private fun eingeklappt(vararg slugs: String) = slugs.fold(RoleStyles.DEFAULT) { stile, slug ->
        stile.with(slug, RoleStyles.defaultFor(slug).copy(showInTimeline = false))
    }

    private fun eintraege(vararg ids: Pair<Long, Long>) =
        ids.map { (id, authorId) ->
            ThreadEntry(comment = testComment(id, authorId = authorId), depth = 0)
        }

    @Test
    fun `ohne eingeklappte Rollen steht jeder Kommentar fuer sich`() {
        val rows = Timeline.rows(eintraege(1L to 0L, 2L to 7L), team, RoleStyles.DEFAULT)

        assertEquals(2, rows.size)
        assertTrue(rows.all { it is TimelineRow.Single })
    }

    @Test
    fun `aufeinanderfolgende Beitraege einer eingeklappten Rolle werden zusammengefasst`() {
        val rows = Timeline.rows(
            eintraege(1L to 0L, 2L to 7L, 3L to 7L, 4L to 0L),
            team,
            eingeklappt(Team.EDITOR),
        )

        assertEquals(3, rows.size)
        val gruppe = rows[1] as TimelineRow.TeamGroup
        assertEquals(2, gruppe.count)
        assertEquals(listOf(2L, 3L), gruppe.entries.map { it.comment.id })
        // Die Rolle wird beim Namen genannt: Dass da etwas ist, reicht nicht,
        // man soll auch sehen, aus welcher Ecke.
        assertEquals(listOf(redaktion), gruppe.roles)
    }

    @Test
    fun `ein einzelner Beitrag wird nicht eingeklappt`() {
        // Eine Karte gegen eine Zeile zu tauschen spart keinen Platz, kostet
        // aber einen Handgriff.
        val rows = Timeline.rows(
            eintraege(1L to 0L, 2L to 7L, 3L to 0L),
            team,
            eingeklappt(Team.EDITOR),
        )

        assertEquals(3, rows.size)
        assertTrue(rows.all { it is TimelineRow.Single })
    }

    @Test
    fun `mehrere eingeklappte Rollen landen in derselben Gruppe`() {
        val rows = Timeline.rows(
            eintraege(1L to 7L, 2L to 8L),
            team,
            eingeklappt(Team.EDITOR, Team.ADMINISTRATOR),
        )

        val gruppe = rows.single() as TimelineRow.TeamGroup
        assertEquals(listOf(redaktion, leitung), gruppe.roles)
    }

    @Test
    fun `ein Kommentar dazwischen trennt zwei Gruppen`() {
        // Die Reihenfolge ist beim Faden die Gestalt des Gespraechs;
        // umzusortieren, um mehr zusammenlegen zu koennen, zerstoerte genau das.
        val rows = Timeline.rows(
            eintraege(1L to 7L, 2L to 7L, 3L to 0L, 4L to 7L, 5L to 7L),
            team,
            eingeklappt(Team.EDITOR),
        )

        assertEquals(3, rows.size)
        assertTrue(rows[0] is TimelineRow.TeamGroup)
        assertTrue(rows[1] is TimelineRow.Single)
        assertTrue(rows[2] is TimelineRow.TeamGroup)
        // Verschiedene Kennungen, sonst verwechselt LazyColumn die Zeilen.
        assertTrue(rows[0].key != rows[2].key)
    }

    @Test
    fun `der Zusammenhang eines Fadens bleibt sichtbar`() {
        // Ein Zusammenhangs-Eintrag steht nur da, damit die Antwort darunter
        // nicht ohne die Frage dasteht. Eingeklappt fehlte genau der Bezug,
        // dessentwegen er geladen wurde.
        val rows = Timeline.rows(
            listOf(
                ThreadEntry(testComment(1, authorId = 7), depth = 0, isContext = true),
                ThreadEntry(testComment(2, authorId = 7), depth = 1),
                ThreadEntry(testComment(3, authorId = 7), depth = 1),
            ),
            team,
            eingeklappt(Team.EDITOR),
        )

        assertEquals(2, rows.size)
        assertTrue(rows[0] is TimelineRow.Single)
        assertEquals(2, (rows[1] as TimelineRow.TeamGroup).count)
    }

    @Test
    fun `die Gruppe uebernimmt die geringste Einrueckung`() {
        val rows = Timeline.rows(
            listOf(
                ThreadEntry(testComment(1, authorId = 7), depth = 2),
                ThreadEntry(testComment(2, authorId = 7), depth = 3),
            ),
            team,
            eingeklappt(Team.EDITOR),
        )

        assertEquals(2, (rows.single() as TimelineRow.TeamGroup).depth)
    }

    @Test
    fun `Gaeste werden nie eingeklappt`() {
        // Gaeste tragen bei WordPress die 0 und gehoeren nie zum Team - sie
        // sind genau die, um die es beim Moderieren geht.
        val rows = Timeline.rows(eintraege(1L to 0L, 2L to 0L), team, eingeklappt(Team.EDITOR))

        assertEquals(2, rows.size)
    }
}
