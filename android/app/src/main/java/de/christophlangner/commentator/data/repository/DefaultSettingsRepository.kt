package de.christophlangner.commentator.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import de.christophlangner.commentator.core.AppLog
import de.christophlangner.commentator.domain.model.CommentFilter
import de.christophlangner.commentator.domain.model.NotifyScope
import de.christophlangner.commentator.domain.model.RoleAccent
import de.christophlangner.commentator.domain.model.RoleStyle
import de.christophlangner.commentator.domain.model.RoleStyles
import de.christophlangner.commentator.domain.model.Team
import de.christophlangner.commentator.domain.repository.AppSettings
import de.christophlangner.commentator.domain.repository.SettingsRepository
import de.christophlangner.commentator.domain.repository.SiteSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Einstellungen in DataStore.
 *
 * Blogbezogene Werte liegen unter einem Schlüssel mit angehängter Kennung.
 * Fehlt er, greift der frühere blogübergreifende Schlüssel: Als es nur einen
 * Blog gab, galten diese Werte für ihn, und genau dort sollen sie bleiben.
 * Geschrieben wird immer der blogbezogene Schlüssel - der alte bleibt
 * unangetastet stehen und wird nur noch gelesen.
 */
@Singleton
class DefaultSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            notificationsEnabled = prefs[NOTIFICATIONS] ?: AppSettings.DEFAULT.notificationsEnabled,
            syncIntervalMinutes = prefs[INTERVAL] ?: AppSettings.DEFAULT_SYNC_INTERVAL_MINUTES,
            showAvatars = prefs[AVATARS] ?: AppSettings.DEFAULT.showAvatars,
            showAuthorEmail = prefs[AUTHOR_EMAIL] ?: AppSettings.DEFAULT.showAuthorEmail,
            threadedInbox = prefs[THREADED] ?: AppSettings.DEFAULT.threadedInbox,
            lastFilter = lastFilterOf(prefs),
        )
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[NOTIFICATIONS] = enabled }
    }

    override suspend fun setSyncIntervalMinutes(minutes: Int) {
        dataStore.edit {
            it[INTERVAL] = minutes.coerceAtLeast(AppSettings.MIN_SYNC_INTERVAL_MINUTES)
        }
    }

    override suspend fun setShowAvatars(show: Boolean) {
        dataStore.edit { it[AVATARS] = show }
    }

    override suspend fun setShowAuthorEmail(show: Boolean) {
        dataStore.edit { it[AUTHOR_EMAIL] = show }
    }

    override suspend fun setThreadedInbox(threaded: Boolean) {
        dataStore.edit { it[THREADED] = threaded }
    }

    override suspend fun setLastFilter(filter: CommentFilter) {
        dataStore.edit { it[LAST_FILTER] = filter.name }
    }

    /**
     * Der gemerkte Filter.
     *
     * Ein Name, den es nicht mehr gibt, faellt auf die Voreinstellung
     * zurueck: Lieber der Posteingang als ein Absturz, wenn aus einer
     * aelteren Fassung ein unbekannter Wert dasteht.
     */
    private fun lastFilterOf(prefs: Preferences): CommentFilter {
        val gespeichert = prefs[LAST_FILTER] ?: return AppSettings.DEFAULT.lastFilter
        return CommentFilter.entries.firstOrNull { it.name == gespeichert }
            ?: AppSettings.DEFAULT.lastFilter
    }

    override fun siteSettings(instanceId: String): Flow<SiteSettings> =
        dataStore.data.map { prefs ->
            SiteSettings(
                notificationsEnabled = prefs[siteNotificationsKey(instanceId)]
                    ?: SiteSettings.DEFAULT.notificationsEnabled,
                notifyScope = notifyScopeOf(prefs, instanceId),
                teamRoles = prefs[teamRolesKey(instanceId)]
                    ?: prefs[TEAM_ROLES]
                    ?: Team.DEFAULT_ROLES,
                roleStyles = roleStylesOf(prefs, instanceId),
                instantPush = prefs[instantPushKey(instanceId)] ?: false,
                pushEndpoint = prefs[pushEndpointKey(instanceId)],
            )
        }

    override suspend fun setInstantPush(instanceId: String, enabled: Boolean) {
        dataStore.edit { it[instantPushKey(instanceId)] = enabled }
    }

    override suspend fun setPushEndpoint(instanceId: String, endpoint: String?) {
        dataStore.edit { prefs ->
            if (endpoint == null) {
                prefs.remove(pushEndpointKey(instanceId))
            } else {
                prefs[pushEndpointKey(instanceId)] = endpoint
            }
        }
    }

    override suspend fun setSiteNotificationsEnabled(instanceId: String, enabled: Boolean) {
        dataStore.edit { it[siteNotificationsKey(instanceId)] = enabled }
    }

    override suspend fun setNotifyScope(instanceId: String, scope: NotifyScope) {
        dataStore.edit { it[notifyScopeKey(instanceId)] = scope.name }
    }

    override suspend fun setTeamRoles(instanceId: String, roles: Set<String>) {
        dataStore.edit { it[teamRolesKey(instanceId)] = roles }
    }

    override suspend fun setRoleStyle(instanceId: String, slug: String, style: RoleStyle) {
        // Gelesen wird der Stand, wie die App ihn sieht - einschliesslich der
        // uebernommenen alten Einstellung. Damit wird sie beim ersten
        // Anfassen festgeschrieben, statt beim naechsten Lesen erneut aus dem
        // alten Schluessel hergeleitet zu werden.
        val aktuell = siteSettings(instanceId).first().roleStyles.with(slug, style)
        dataStore.edit { prefs ->
            prefs[roleStylesKey(instanceId)] = JSON.encodeToString(
                aktuell.byRole.mapValues(::gespeichert),
            )
        }
    }

    /**
     * Liest den Umfang, mit Ruecksicht auf die frueheren Schluessel.
     *
     * Zwei Stufen liegen hier hintereinander: der blogübergreifende Wert aus
     * der Zeit mit nur einem Blog und davor ein reiner Schalter "nur
     * Moderation" ja/nein. Wer eines von beiden eingestellt hatte, soll es
     * behalten, statt stillschweigend auf die Voreinstellung zurueckgesetzt
     * zu werden.
     */
    private fun notifyScopeOf(prefs: Preferences, instanceId: String): NotifyScope {
        prefs[notifyScopeKey(instanceId)]?.let { return NotifyScope.fromStorage(it) }
        prefs[NOTIFY_SCOPE]?.let { return NotifyScope.fromStorage(it) }
        return if (prefs[ONLY_PENDING] == true) NotifyScope.PENDING else NotifyScope.DEFAULT
    }

    /**
     * Liest die Rolleneinstellungen, mit Ruecksicht auf die frueheren Schluessel.
     *
     * Wie beim Umfang zwei Stufen: der blogübergreifende Eintrag und davor
     * "Team-Kommentare ausblenden" fuer alle Rollen gemeinsam. Wer das
     * eingeschaltet hatte, bekommt sie jetzt eingeklappt statt ausgeblendet -
     * naeher am Gewollten als ein stilles Zurueckfallen auf "alles anzeigen".
     */
    private fun roleStylesOf(prefs: Preferences, instanceId: String): RoleStyles {
        val roh = prefs[roleStylesKey(instanceId)] ?: prefs[ROLE_STYLES]
        if (roh != null) {
            val gelesen = runCatching {
                JSON.decodeFromString<Map<String, GespeicherterStil>>(roh)
            }.getOrElse {
                // Lieber die Voreinstellung als ein Absturz beim Start: Eine
                // unlesbare Zeile wird beim naechsten Speichern ersetzt.
                AppLog.d("Rolleneinstellungen unlesbar, nutze Voreinstellung")
                return RoleStyles.DEFAULT
            }
            return RoleStyles(gelesen.mapValues { (slug, stil) -> stil.toDomain(slug) })
        }

        if (prefs[HIDE_TEAM] != true) return RoleStyles.DEFAULT
        // Das eigene Konto war vom alten Schalter mit erfasst - es zaehlt
        // immer zum Team.
        val rollen = (prefs[teamRolesKey(instanceId)] ?: prefs[TEAM_ROLES] ?: Team.DEFAULT_ROLES) +
            Team.SELF
        return RoleStyles(
            rollen.associateWith { slug ->
                RoleStyles.defaultFor(slug).copy(showInTimeline = false)
            },
        )
    }

    /**
     * Die gespeicherte Form.
     *
     * Eigener Typ und nicht [RoleStyle] selbst: Am Domaenenmodell darf sich
     * etwas aendern, ohne dass die Dateien auf dem Geraet unlesbar werden.
     * Fehlende Felder bekommen hier die Voreinstellung.
     */
    @Serializable
    private data class GespeicherterStil(
        val accent: String? = null,
        val color: Boolean = true,
        val timeline: Boolean = true,
        /**
         * Offen gelassen und nicht auf `true` gesetzt: Was hier fehlt, soll
         * die Voreinstellung der Rolle bekommen - und die ist beim eigenen
         * Konto "nicht melden".
         */
        val notify: Boolean? = null,
    ) {
        fun toDomain(slug: String) = RoleStyle(
            accent = accent?.let(RoleAccent::fromStorage) ?: RoleStyles.defaultFor(slug).accent,
            colorEnabled = color,
            showInTimeline = timeline,
            notify = notify ?: RoleStyles.defaultFor(slug).notify,
        )
    }

    private fun gespeichert(eintrag: Map.Entry<String, RoleStyle>) = GespeicherterStil(
        accent = eintrag.value.accent.name,
        color = eintrag.value.colorEnabled,
        timeline = eintrag.value.showInTimeline,
        notify = eintrag.value.notify,
    )

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }

        // Blogübergreifend.
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val INTERVAL = intPreferencesKey("sync_interval_minutes")
        val AVATARS = booleanPreferencesKey("show_avatars")
        val AUTHOR_EMAIL = booleanPreferencesKey("show_author_email")
        val THREADED = booleanPreferencesKey("threaded_inbox")
        val LAST_FILTER = stringPreferencesKey("last_filter")

        // Nur noch gelesen: Stand aus der Zeit, als die App einen Blog kannte.
        val ONLY_PENDING = booleanPreferencesKey("notify_only_pending")
        val NOTIFY_SCOPE = stringPreferencesKey("notify_scope")
        val TEAM_ROLES = stringSetPreferencesKey("team_roles")
        val HIDE_TEAM = booleanPreferencesKey("hide_team_comments")
        val ROLE_STYLES = stringPreferencesKey("role_styles")

        // Je Blog.
        fun siteNotificationsKey(instanceId: String) =
            booleanPreferencesKey("notifications_enabled_$instanceId")

        fun notifyScopeKey(instanceId: String) = stringPreferencesKey("notify_scope_$instanceId")

        fun teamRolesKey(instanceId: String) = stringSetPreferencesKey("team_roles_$instanceId")

        fun roleStylesKey(instanceId: String) = stringPreferencesKey("role_styles_$instanceId")

        fun instantPushKey(instanceId: String) = booleanPreferencesKey("instant_push_$instanceId")

        fun pushEndpointKey(instanceId: String) = stringPreferencesKey("push_endpoint_$instanceId")
    }
}
