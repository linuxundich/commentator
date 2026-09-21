package de.christophlangner.commentator.domain.repository

import de.christophlangner.commentator.domain.model.NotifyScope
import de.christophlangner.commentator.domain.model.Team
import kotlinx.coroutines.flow.Flow

/** Vom Benutzer steuerbare Einstellungen. */
data class AppSettings(
    val notificationsEnabled: Boolean,
    val syncIntervalMinutes: Int,
    /** Avatare bauen eine Verbindung zu Gravatar auf und sind deshalb abschaltbar. */
    val showAvatars: Boolean,
    /** E-Mail-Adressen der Kommentatoren in der Detailansicht anzeigen. */
    val showAuthorEmail: Boolean,
    /** Worueber die Hintergrundpruefung benachrichtigt. */
    val notifyScope: NotifyScope,
    /** Rollen, deren Kommentare als Beitrag des Teams gekennzeichnet werden. */
    val teamRoles: Set<String>,
    /**
     * Kommentare des Teams aus den Uebersichten heraushalten.
     *
     * Sie muessen in aller Regel nicht moderiert werden. Standardmaessig aus:
     * Kommentare ungefragt zu verbergen waere eine unangenehme Ueberraschung.
     */
    val hideTeamComments: Boolean,
    /**
     * Die Liste als Gespraechsfaden statt rein chronologisch.
     *
     * Voreingestellt an: Eine Antwort ohne die Frage darueber ist schwerer zu
     * beurteilen als eine Frage ohne Antwort.
     */
    val threadedInbox: Boolean,
) {
    companion object {
        /**
         * Untergrenze für periodische Arbeit in WorkManager. Kürzere Werte
         * würde die Plattform ohnehin auf diesen anheben - besser, das schon
         * hier zu wissen, als es dem Benutzer zu versprechen.
         */
        const val MIN_SYNC_INTERVAL_MINUTES = 15

        const val DEFAULT_SYNC_INTERVAL_MINUTES = 15

        val DEFAULT = AppSettings(
            notificationsEnabled = true,
            syncIntervalMinutes = DEFAULT_SYNC_INTERVAL_MINUTES,
            showAvatars = false,
            showAuthorEmail = false,
            notifyScope = NotifyScope.DEFAULT,
            teamRoles = Team.DEFAULT_ROLES,
            hideTeamComments = false,
            threadedInbox = true,
        )
    }
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setSyncIntervalMinutes(minutes: Int)
    suspend fun setShowAvatars(show: Boolean)
    suspend fun setShowAuthorEmail(show: Boolean)
    suspend fun setNotifyScope(scope: NotifyScope)
    suspend fun setTeamRoles(roles: Set<String>)
    suspend fun setHideTeamComments(hide: Boolean)
    suspend fun setThreadedInbox(threaded: Boolean)
}
