package de.christophlangner.commentator.domain

import de.christophlangner.commentator.domain.model.Team
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import de.christophlangner.commentator.domain.model.TeamRole
import org.junit.Test

private fun rolle(slug: String) = TeamRole(slug, slug)

class TeamTest {

    @Test
    fun `Gaeste gehoeren nie zum Team`() {
        // WordPress traegt bei Gastkommentaren 0 ein. Waere 0 als Mitglied
        // moeglich, waere jeder Gast plötzlich Team.
        val team = Team(members = mapOf(0L to rolle("editor"), 2L to rolle("editor")))

        assertFalse(team.contains(0L))
        assertTrue(team.contains(2L))
    }

    @Test
    fun `unbekannte Nutzer gehoeren nicht dazu`() {
        assertFalse(Team(members = mapOf(1L to rolle("editor"), 2L to rolle("editor"))).contains(7L))
    }

    @Test
    fun `ohne ermitteltes Team ist niemand Team`() {
        assertFalse(Team().contains(1L))
    }

    @Test
    fun `Voreinstellung umfasst Administrator und Redakteur`() {
        // Autoren und Mitarbeiter schreiben Beitraege, moderieren aber nicht.
        assertTrue("administrator" in Team.DEFAULT_ROLES)
        assertTrue("editor" in Team.DEFAULT_ROLES)
        assertFalse("author" in Team.DEFAULT_ROLES)
        assertFalse("subscriber" in Team.DEFAULT_ROLES)
    }
}
