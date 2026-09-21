package de.christophlangner.commentator.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import de.christophlangner.commentator.domain.repository.AppSettings
import de.christophlangner.commentator.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

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
            notifyOnlyPending = prefs[ONLY_PENDING] ?: AppSettings.DEFAULT.notifyOnlyPending,
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

    override suspend fun setNotifyOnlyPending(onlyPending: Boolean) {
        dataStore.edit { it[ONLY_PENDING] = onlyPending }
    }

    private companion object {
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val INTERVAL = intPreferencesKey("sync_interval_minutes")
        val AVATARS = booleanPreferencesKey("show_avatars")
        val AUTHOR_EMAIL = booleanPreferencesKey("show_author_email")
        val ONLY_PENDING = booleanPreferencesKey("notify_only_pending")
    }
}
