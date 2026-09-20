package de.christophlangner.commentator.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val isSignedOut: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val signedOut = kotlinx.coroutines.flow.MutableStateFlow(false)

    val state: StateFlow<SettingsUiState> = combine(
        authRepository.observeActiveInstance(),
        settingsRepository.settings,
        signedOut,
    ) { instance, settings, isSignedOut ->
        SettingsUiState(instance = instance, settings = settings, isSignedOut = isSignedOut)
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

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
