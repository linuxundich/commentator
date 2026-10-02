package de.christophlangner.commentator.notification

import de.christophlangner.commentator.domain.repository.SiteSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Die Prüfung wird nur dann seltener, wenn wirklich jeder meldende Blog sofort meldet. */
class SyncIntervalTest {

    private val mitPush = SiteSettings.DEFAULT.copy(instantPush = true, pushEndpoint = "https://ntfy.sh/up1")
    private val ohnePush = SiteSettings.DEFAULT

    @Test
    fun `alle meldenden Blogs mit Push strecken die Pruefung`() {
        assertTrue(SyncScheduler.pushCoversAll(listOf(mitPush, mitPush)))
        assertEquals(360, SyncScheduler.effectiveInterval(15, pushCoversAll = true))
    }

    @Test
    fun `ein Blog ohne Push behaelt das eingestellte Intervall`() {
        assertFalse(SyncScheduler.pushCoversAll(listOf(mitPush, ohnePush)))
        assertEquals(15, SyncScheduler.effectiveInterval(15, pushCoversAll = false))
    }

    @Test
    fun `eingeschaltet ohne hinterlegte Adresse zaehlt nicht`() {
        assertFalse(SyncScheduler.pushCoversAll(listOf(mitPush.copy(pushEndpoint = null))))
    }

    @Test
    fun `stumme Blogs zaehlen nicht mit`() {
        assertTrue(SyncScheduler.pushCoversAll(listOf(mitPush, ohnePush.copy(notificationsEnabled = false))))
    }

    @Test
    fun `ein laengeres eingestelltes Intervall bleibt`() {
        assertEquals(720, SyncScheduler.effectiveInterval(720, pushCoversAll = true))
    }
}
