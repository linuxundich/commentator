package de.christophlangner.commentator.domain.repository

import de.christophlangner.commentator.domain.model.CommentFilter
import de.christophlangner.commentator.domain.model.NotifyScope
import de.christophlangner.commentator.domain.model.RoleStyle
import de.christophlangner.commentator.domain.model.RoleStyles
import de.christophlangner.commentator.domain.model.Team
import kotlinx.coroutines.flow.Flow

/**
 * Einstellungen, die fuer alle Blogs gemeinsam gelten.
 *
 * Hier steht nur, was blogunabhaengig ist. Alles, was von einem einzelnen
 * Blog abhaengt - was dort als Team gilt, welche Rollen melden -, steht in
 * [SiteSettings]. Die Trennung ist nicht bloss Ordnung: Waeren die Rollen
 * global, wuerde eine Redaktion auf einem Blog die Farben eines anderen
 * mitverstellen.
 */
data class AppSettings(
    /**
     * Hauptschalter fuer die Hintergrundpruefung.
     *
     * Aus heisst: kein Blog meldet, unabhaengig von seiner eigenen
     * Einstellung. Der Schalter je Blog steht in
     * [SiteSettings.notificationsEnabled].
     */
    val notificationsEnabled: Boolean,
    /**
     * Prueftakt, bewusst global.
     *
     * Die Hintergrundpruefung laeuft in einem Durchgang ueber alle Blogs.
     * Ein Takt je Blog wuerde das Geraet oefter wecken, ohne mehr zu
     * liefern.
     */
    val syncIntervalMinutes: Int,
    /** Avatare bauen eine Verbindung zu Gravatar auf und sind deshalb abschaltbar. */
    val showAvatars: Boolean,
    /** E-Mail-Adressen der Kommentatoren in der Detailansicht anzeigen. */
    val showAuthorEmail: Boolean,
    /**
     * Die Liste als Gespraechsfaden statt rein chronologisch.
     *
     * Voreingestellt an: Eine Antwort ohne die Frage darueber ist schwerer zu
     * beurteilen als eine Frage ohne Antwort.
     */
    val threadedInbox: Boolean,
    /**
     * Der zuletzt gewaehlte Filter des Posteingangs.
     *
     * Bloguebergreifend, wie der Filter selbst: Ein Wechsel des Blogs nimmt
     * ihn mit, weil wer offene Kommentare sichtet das auf dem naechsten Blog
     * auch will. Waere er je Blog gespeichert, spraenge er beim Umschalten -
     * und das waere genau die Gewohnheit, die hier festgehalten werden soll.
     *
     * Keine Einstellung, die jemand von Hand setzt, sondern eine gemerkte
     * Gewohnheit. Sie steht hier, weil hier alles Blogunabhaengige steht.
     */
    val lastFilter: CommentFilter,
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
            threadedInbox = true,
            lastFilter = CommentFilter.PENDING,
        )
    }
}

/**
 * Einstellungen eines einzelnen Blogs.
 *
 * Jeder Blog hat eine eigene Redaktion, eigene Rollen und einen eigenen Takt,
 * in dem dort etwas passiert. Ein Nebenprojekt darf still bleiben, waehrend
 * der Hauptblog meldet.
 */
data class SiteSettings(
    /**
     * Ob dieser Blog benachrichtigt.
     *
     * Wirkt nur, solange auch [AppSettings.notificationsEnabled] gesetzt ist -
     * der Hauptschalter hat Vorrang.
     */
    val notificationsEnabled: Boolean,
    /** Worueber die Hintergrundpruefung fuer diesen Blog benachrichtigt. */
    val notifyScope: NotifyScope,
    /** Rollen, deren Kommentare als Beitrag des Teams gekennzeichnet werden. */
    val teamRoles: Set<String>,
    /**
     * Farbe, Sichtbarkeit und Benachrichtigung je Rolle.
     *
     * Was hier nicht eingetragen ist, gilt in der Voreinstellung - die
     * Rollen eines Blogs stehen erst fest, wenn er befragt wurde.
     */
    val roleStyles: RoleStyles,
) {
    companion object {
        val DEFAULT = SiteSettings(
            notificationsEnabled = true,
            notifyScope = NotifyScope.DEFAULT,
            teamRoles = Team.DEFAULT_ROLES,
            roleStyles = RoleStyles.DEFAULT,
        )
    }
}

interface SettingsRepository {

    val settings: Flow<AppSettings>
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setSyncIntervalMinutes(minutes: Int)
    suspend fun setShowAvatars(show: Boolean)
    suspend fun setShowAuthorEmail(show: Boolean)
    suspend fun setThreadedInbox(threaded: Boolean)

    /** Haelt fest, welchen Filter der Posteingang zuletzt zeigte. */
    suspend fun setLastFilter(filter: CommentFilter)

    /**
     * Die Einstellungen eines Blogs.
     *
     * Immer mit ausdruecklicher Kennung, nie "der aktive Blog": Die
     * Hintergrundpruefung geht alle Blogs durch, und der aktive ist dabei
     * keiner von besonderer Bedeutung.
     */
    fun siteSettings(instanceId: String): Flow<SiteSettings>
    suspend fun setSiteNotificationsEnabled(instanceId: String, enabled: Boolean)
    suspend fun setNotifyScope(instanceId: String, scope: NotifyScope)
    suspend fun setTeamRoles(instanceId: String, roles: Set<String>)
    suspend fun setRoleStyle(instanceId: String, slug: String, style: RoleStyle)
}
