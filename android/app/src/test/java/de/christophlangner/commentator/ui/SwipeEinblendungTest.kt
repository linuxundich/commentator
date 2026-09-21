package de.christophlangner.commentator.ui

import de.christophlangner.commentator.ui.inbox.einblendgrad
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regeln für das Einblenden der Zeichen unter einer gewischten Karte.
 *
 * Am Gerät zeigte sich beim kurzen Ziehen ein halbes Symbol und ein mitten im
 * Wort abgeschnittenes Wort. Diese Tests halten fest, dass ein Zeichen erst
 * auftaucht, wenn es vollständig neben die Karte passt.
 */
class SwipeEinblendungTest {

    @Test
    fun `ohne Platz ist nichts zu sehen`() {
        assertEquals(0f, einblendgrad(freigelegt = 0f, ab = 100f, ueberblendung = 50f), 0f)
        assertEquals(0f, einblendgrad(freigelegt = 99f, ab = 100f, ueberblendung = 50f), 0f)
    }

    @Test
    fun `genau an der Grenze faengt das Aufblenden erst an`() {
        assertEquals(0f, einblendgrad(freigelegt = 100f, ab = 100f, ueberblendung = 50f), 0f)
    }

    @Test
    fun `dazwischen wird weich aufgeblendet`() {
        assertEquals(0.5f, einblendgrad(freigelegt = 125f, ab = 100f, ueberblendung = 50f), 0.001f)
    }

    @Test
    fun `mit genug Platz ist das Zeichen voll da und bleibt es`() {
        assertEquals(1f, einblendgrad(freigelegt = 150f, ab = 100f, ueberblendung = 50f), 0f)
        assertEquals(1f, einblendgrad(freigelegt = 900f, ab = 100f, ueberblendung = 50f), 0f)
    }

    @Test
    fun `ohne Ueberblendstrecke wird hart geschaltet`() {
        // Sonst käme es zu einer Division durch null.
        assertEquals(0f, einblendgrad(freigelegt = 99f, ab = 100f, ueberblendung = 0f), 0f)
        assertEquals(1f, einblendgrad(freigelegt = 100f, ab = 100f, ueberblendung = 0f), 0f)
    }
}
