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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val instance: WordPressInstance? = null,
    val settings: AppSettings = AppSettings.DEFAULT,
    val appIcon: AppIcon = AppIcon.DEFAULT,
    val isSignedOut: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val appIconManager: AppIconManager,
) : ViewModel() {

    private val signedOut = kotlinx.coroutines.flow.MutableStateFlow(false)

    val state: StateFlow<SettingsUiState> = combine(
        authRepository.observeActiveInstance(),
        settingsRepository.settings,
        appIconManager.current,
        signedOut,
    ) { instance, settings, appIcon, isSignedOut ->
        SettingsUiState(
            instance = instance,
            settings = settings,
            appIcon = appIcon,
            isSignedOut = isSignedOut,
        )
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    init {
        // Der Bildschirm zeigt, ob das Bridge-Plugin erkannt wurde. Wird es
        // nachträglich installiert, soll ein Blick in die Einstellungen
        // genügen, damit die App es bemerkt.
        viewModelScope.launch { authRepository.refreshSiteCapabilities() }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNotificationsEnabled(enabled) }
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

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            signedOut.value = true
        }
    }

    /** Auswahlmöglichkeiten für das Prüfintervall in Minuten. */
    val intervalOptions: List<Int> =
        listOf(AppSettings.MIN_SYNC_INTERVAL_MINUTES, 30, 60, 180, 360)
}
