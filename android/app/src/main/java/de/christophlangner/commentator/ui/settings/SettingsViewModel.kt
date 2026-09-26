package de.christophlangner.commentator.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.christophlangner.commentator.data.system.AppIconManager
import de.christophlangner.commentator.domain.model.AppIcon
import de.christophlangner.commentator.domain.model.WordPressInstance
import de.christophlangner.commentator.domain.repository.AppSettings
import de.christophlangner.commentator.domain.repository.AuthRepository
import de.christophlangner.commentator.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    /** Alle eingerichteten Blogs; jeder führt in seine eigenen Einstellungen. */
    val instances: List<WordPressInstance> = emptyList(),
    /** Der Blog, den der Posteingang gerade zeigt - nur zur Kennzeichnung. */
    val activeInstanceId: String? = null,
    val settings: AppSettings = AppSettings.DEFAULT,
    val appIcon: AppIcon = AppIcon.DEFAULT,
)

/**
 * Die Einstellungen, die für alle Blogs gelten.
 *
 * Was an einem einzelnen Blog hängt, liegt im [SiteSettingsViewModel] - und
 * zwar mit ausdrücklicher Kennung, nicht als "der aktive Blog".
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val appIconManager: AppIconManager,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        authRepository.observeInstances(),
        authRepository.observeActiveInstance().map { it?.id },
        settingsRepository.settings,
        appIconManager.current,
    ) { instances, activeId, settings, appIcon ->
        SettingsUiState(
            instances = instances,
            activeInstanceId = activeId,
            settings = settings,
            appIcon = appIcon,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNotificationsEnabled(enabled) }
    }

    fun setThreadedInbox(threaded: Boolean) {
        viewModelScope.launch { settingsRepository.setThreadedInbox(threaded) }
    }

    fun setSyncInterval(minutes: Int) {
        viewModelScope.launch { settingsRepository.setSyncIntervalMinutes(minutes) }
    }

    fun setShowAvatars(show: Boolean) {
        viewModelScope.launch { settingsRepository.setShowAvatars(show) }
    }

    fun setShowAuthorEmail(show: Boolean) {
        viewModelScope.launch { settingsRepository.setShowAuthorEmail(show) }
    }

    /**
     * Wechselt das Startsymbol. Wirkt sofort auf den Startbildschirm, deshalb
     * ohne Umweg über eine gespeicherte Einstellung - maßgeblich ist der
     * Zustand im System.
     */
    fun setAppIcon(icon: AppIcon) {
        appIconManager.select(icon)
    }

    /** Auswahlmöglichkeiten für das Prüfintervall in Minuten. */
    val intervalOptions: List<Int> =
        listOf(AppSettings.MIN_SYNC_INTERVAL_MINUTES, 30, 60, 180, 360)
}
