package de.christophlangner.commentator.domain

import de.christophlangner.commentator.domain.model.Team
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamTest {

    @Test
    fun `Gaeste gehoeren nie zum Team`() {
        // WordPress traegt bei Gastkommentaren 0 ein. Waere 0 als Mitglied
        // moeglich, waere jeder Gast plötzlich Team.
        val team = Team(memberIds = setOf(0L, 2L))

        assertFalse(team.contains(0L))
        assertTrue(team.contains(2L))
    }

    @Test
    fun `unbekannte Nutzer gehoeren nicht dazu`() {
        assertFalse(Team(memberIds = setOf(1L, 2L)).contains(7L))
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
