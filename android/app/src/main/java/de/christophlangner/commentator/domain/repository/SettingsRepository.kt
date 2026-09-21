package de.christophlangner.commentator.domain.repository

import kotlinx.coroutines.flow.Flow

/** Vom Benutzer steuerbare Einstellungen. */
data class AppSettings(
    val notificationsEnabled: Boolean,
    val syncIntervalMinutes: Int,
    /** Avatare bauen eine Verbindung zu Gravatar auf und sind deshalb abschaltbar. */
    val showAvatars: Boolean,
    /** E-Mail-Adressen der Kommentatoren in der Detailansicht anzeigen. */
    val showAuthorEmail: Boolean,
    /**
     * Nur ueber Kommentare benachrichtigen, die auf Moderation warten.
     *
     * Standardmaessig aus: Blogs, die Kommentare automatisch freischalten,
     * haetten sonst nie etwas zu melden - und gerade dort ist ein neuer
     * Kommentar bereits oeffentlich und damit eher dringlicher.
     */
    val notifyOnlyPending: Boolean,
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
            notifyOnlyPending = false,
        )
    }
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setSyncIntervalMinutes(minutes: Int)
    suspend fun setShowAvatars(show: Boolean)
    suspend fun setShowAuthorEmail(show: Boolean)
    suspend fun setNotifyOnlyPending(onlyPending: Boolean)
}
