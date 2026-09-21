package de.christophlangner.commentator.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import de.christophlangner.commentator.domain.model.NotifyScope
import de.christophlangner.commentator.domain.repository.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DefaultSettingsRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun dataStore(name: String = "settings") = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { File(temporaryFolder.root, "$name.preferences_pb") },
    )

    private fun repository() = DefaultSettingsRepository(dataStore())

    @Test
    fun `Voreinstellungen sind datensparsam`() = runTest {
        val settings = repository().settings.first()

        // Beides erzeugt eine Verbindung zu Dritten beziehungsweise zeigt
        // personenbezogene Daten und ist deshalb standardmäßig aus.
        assertFalse(settings.showAvatars)
        assertFalse(settings.showAuthorEmail)
        assertTrue(settings.notificationsEnabled)
        assertEquals(AppSettings.DEFAULT_SYNC_INTERVAL_MINUTES, settings.syncIntervalMinutes)
    }

    @Test
    fun `Einstellungen ueberdauern das Speichern`() = runTest {
        val repository = repository()

        repository.setShowAvatars(true)
        repository.setNotificationsEnabled(false)
        repository.setSyncIntervalMinutes(60)

        val settings = repository.settings.first()
        assertTrue(settings.showAvatars)
        assertFalse(settings.notificationsEnabled)
        assertEquals(60, settings.syncIntervalMinutes)
    }

    @Test
    fun `zu kurzes Intervall wird auf die Untergrenze angehoben`() = runTest {
        val repository = repository()

        repository.setSyncIntervalMinutes(1)

        // WorkManager lässt für periodische Arbeit nichts Kürzeres zu; die
        // Zusage eines Ein-Minuten-Intervalls wäre schlicht falsch.
        assertEquals(
            AppSettings.MIN_SYNC_INTERVAL_MINUTES,
            repository.settings.first().syncIntervalMinutes,
        )
    }
    @Test
    fun `ein frueher gesetzter Schalter bleibt erhalten`() = runTest {
        // Vor der dreistufigen Auswahl gab es nur "nur Moderation" ja/nein.
        // Wer das eingeschaltet hatte, darf nicht stillschweigend auf die
        // Voreinstellung zurueckfallen.
        val store = dataStore("alt")
        store.edit { it[booleanPreferencesKey("notify_only_pending")] = true }

        val settings = DefaultSettingsRepository(store).settings.first()

        assertEquals(NotifyScope.PENDING, settings.notifyScope)
    }

    @Test
    fun `ohne gespeicherten Wert gilt die Voreinstellung`() = runTest {
        assertEquals(NotifyScope.NEW_COMMENTS, repository().settings.first().notifyScope)
    }

    @Test
    fun `die neue Auswahl sticht den alten Schalter`() = runTest {
        val store = dataStore("vorrang")
        store.edit { it[booleanPreferencesKey("notify_only_pending")] = true }
        val repository = DefaultSettingsRepository(store)

        repository.setNotifyScope(NotifyScope.EVERYTHING)

        assertEquals(NotifyScope.EVERYTHING, repository.settings.first().notifyScope)
    }
}
