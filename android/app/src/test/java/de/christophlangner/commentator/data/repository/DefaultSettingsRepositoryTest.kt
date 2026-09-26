package de.christophlangner.commentator.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import de.christophlangner.commentator.domain.model.CommentFilter
import de.christophlangner.commentator.domain.model.NotifyScope
import de.christophlangner.commentator.domain.model.RoleAccent
import de.christophlangner.commentator.domain.model.RoleStyles
import de.christophlangner.commentator.domain.model.Team
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

    private companion object {
        const val BLOG = "instance-1"
        const val ZWEITER_BLOG = "instance-2"
    }

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
    fun `der zuletzt gewaehlte Filter ueberdauert den Neustart`() = runTest {
        val store = dataStore("filter")
        DefaultSettingsRepository(store).setLastFilter(CommentFilter.SPAM)

        // Zweite Instanz auf derselben Datei: genau der Fall nach einem
        // Neustart der App.
        val gelesen = DefaultSettingsRepository(store).settings.first()

        assertEquals(CommentFilter.SPAM, gelesen.lastFilter)
    }

    @Test
    fun `ein unbekannter Filter faellt auf den Posteingang zurueck`() = runTest {
        // Etwa nach einer Fassung, die einen Filter kannte, den es nicht
        // mehr gibt. Lieber der Posteingang als ein Absturz beim Start.
        val store = dataStore("filter-unbekannt")
        store.edit { it[stringPreferencesKey("last_filter")] = "ARCHIVIERT" }

        val gelesen = DefaultSettingsRepository(store).settings.first()

        assertEquals(CommentFilter.PENDING, gelesen.lastFilter)
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

        val settings = DefaultSettingsRepository(store).siteSettings(BLOG).first()

        assertEquals(NotifyScope.PENDING, settings.notifyScope)
    }

    @Test
    fun `ohne gespeicherten Wert gilt die Voreinstellung`() = runTest {
        assertEquals(NotifyScope.NEW_COMMENTS, repository().siteSettings(BLOG).first().notifyScope)
    }

    @Test
    fun `die neue Auswahl sticht den alten Schalter`() = runTest {
        val store = dataStore("vorrang")
        store.edit { it[booleanPreferencesKey("notify_only_pending")] = true }
        val repository = DefaultSettingsRepository(store)

        repository.setNotifyScope(BLOG, NotifyScope.EVERYTHING)

        assertEquals(NotifyScope.EVERYTHING, repository.siteSettings(BLOG).first().notifyScope)
    }

    @Test
    fun `Rolleneinstellungen ueberdauern das Speichern`() = runTest {
        val repository = repository()

        repository.setRoleStyle(
            BLOG,
            Team.EDITOR,
            RoleStyles.defaultFor(Team.EDITOR).copy(
                accent = RoleAccent.PFLAUME,
                showInTimeline = false,
                notify = false,
            ),
        )

        val stil = repository.siteSettings(BLOG).first().roleStyles.of(Team.EDITOR)
        assertEquals(RoleAccent.PFLAUME, stil.accent)
        assertFalse(stil.showInTimeline)
        assertFalse(stil.notify)
        // Was nicht angefasst wurde, bleibt bei der Voreinstellung.
        assertTrue(repository.siteSettings(BLOG).first().roleStyles.of(Team.ADMINISTRATOR).notify)
    }

    @Test
    fun `aus ausgeblendetem Team wird eingeklapptes Team`() = runTest {
        // Wer "Team-Kommentare ausblenden" eingeschaltet hatte, wollte sie
        // nicht in der Liste haben. Eingeklappt kommt dem naeher als ein
        // stilles Zurueckfallen auf "alles anzeigen".
        val store = dataStore("altes-ausblenden")
        store.edit {
            it[booleanPreferencesKey("hide_team_comments")] = true
            it[stringSetPreferencesKey("team_roles")] = setOf(Team.ADMINISTRATOR, Team.EDITOR)
        }

        val stile = DefaultSettingsRepository(store).siteSettings(BLOG).first().roleStyles

        assertFalse(stile.of(Team.ADMINISTRATOR).showInTimeline)
        assertFalse(stile.of(Team.EDITOR).showInTimeline)
        // Das eigene Konto war vom alten Schalter mit erfasst.
        assertFalse(stile.of(Team.SELF).showInTimeline)
        // Rollen, die gar nicht als Team gefuehrt wurden, bleibt es erspart.
        assertTrue(stile.of(Team.AUTHOR).showInTimeline)
    }

    @Test
    fun `ohne alten Schalter stehen alle Rollen in der Liste`() = runTest {
        val stile = repository().siteSettings(BLOG).first().roleStyles

        assertTrue(stile.collapsedRoles.isEmpty())
        assertTrue(stile.of(Team.ADMINISTRATOR).colorEnabled)
    }

    @Test
    fun `Rolleneinstellungen eines Blogs lassen den anderen unberuehrt`() = runTest {
        val repository = repository()

        repository.setRoleStyle(
            BLOG,
            Team.EDITOR,
            RoleStyles.defaultFor(Team.EDITOR).copy(accent = RoleAccent.PFLAUME),
        )

        assertEquals(
            RoleAccent.PFLAUME,
            repository.siteSettings(BLOG).first().roleStyles.of(Team.EDITOR).accent,
        )
        // Der zweite Blog hat eine eigene Redaktion und eigene Farben.
        assertEquals(
            RoleStyles.defaultFor(Team.EDITOR).accent,
            repository.siteSettings(ZWEITER_BLOG).first().roleStyles.of(Team.EDITOR).accent,
        )
    }

    @Test
    fun `ein stiller Blog laesst den anderen melden`() = runTest {
        val repository = repository()

        repository.setSiteNotificationsEnabled(BLOG, false)

        assertFalse(repository.siteSettings(BLOG).first().notificationsEnabled)
        assertTrue(repository.siteSettings(ZWEITER_BLOG).first().notificationsEnabled)
        // Der Hauptschalter bleibt davon unberuehrt.
        assertTrue(repository.settings.first().notificationsEnabled)
    }

    @Test
    fun `der Umfang eines Blogs gilt nicht fuer den anderen`() = runTest {
        val repository = repository()

        repository.setNotifyScope(BLOG, NotifyScope.EVERYTHING)

        assertEquals(NotifyScope.EVERYTHING, repository.siteSettings(BLOG).first().notifyScope)
        assertEquals(
            NotifyScope.DEFAULT,
            repository.siteSettings(ZWEITER_BLOG).first().notifyScope,
        )
    }

    @Test
    fun `der frueher blogweite Umfang gilt weiter fuer jeden Blog`() = runTest {
        // Als die App einen Blog kannte, lag der Umfang unter einem Schluessel
        // ohne Kennung. Er darf beim Umstieg nicht verloren gehen.
        val store = dataStore("frueher-blogweit")
        store.edit { it[stringPreferencesKey("notify_scope")] = NotifyScope.PENDING.name }
        val repository = DefaultSettingsRepository(store)

        assertEquals(NotifyScope.PENDING, repository.siteSettings(BLOG).first().notifyScope)

        // Und sobald ein Blog etwas eigenes bekommt, gilt bei ihm nur das.
        repository.setNotifyScope(BLOG, NotifyScope.EVERYTHING)

        assertEquals(NotifyScope.EVERYTHING, repository.siteSettings(BLOG).first().notifyScope)
        assertEquals(NotifyScope.PENDING, repository.siteSettings(ZWEITER_BLOG).first().notifyScope)
    }

    @Test
    fun `die frueher blogweite Teamauswahl gilt weiter`() = runTest {
        val store = dataStore("frueher-team")
        store.edit {
            it[stringSetPreferencesKey("team_roles")] = setOf(Team.ADMINISTRATOR)
        }

        assertEquals(
            setOf(Team.ADMINISTRATOR),
            DefaultSettingsRepository(store).siteSettings(BLOG).first().teamRoles,
        )
    }
}
