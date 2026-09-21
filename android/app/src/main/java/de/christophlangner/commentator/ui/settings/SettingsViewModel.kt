package de.christophlangner.commentator.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.christophlangner.commentator.data.system.AppIconManager
import de.christophlangner.commentator.domain.model.AppIcon
import de.christophlangner.commentator.domain.model.TeamRole
import de.christophlangner.commentator.domain.repository.TeamRepository
import de.christophlangner.commentator.core.Outcome
import kotlinx.coroutines.flow.first
import de.christophlangner.commentator.domain.model.NotifyScope
import de.christophlangner.commentator.domain.model.WordPressInstance
import de.christophlangner.commentator.domain.repository.AppSettings
import de.christophlangner.commentator.domain.repository.AuthRepository
import de.christophlangner.commentator.domain.repository.ReplyTemplate
import de.christophlangner.commentator.domain.repository.ReplyTemplateRepository
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
    val templates: List<ReplyTemplate> = emptyList(),
    /** Rollen, die der Blog kennt. Leer, solange sie nicht ermittelt wurden. */
    val availableRoles: List<TeamRole> = emptyList(),
) {
    val canAddTemplate: Boolean get() = templates.size < ReplyTemplate.MAX_TEMPLATES
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val appIconManager: AppIconManager,
    private val replyTemplateRepository: ReplyTemplateRepository,
    private val teamRepository: TeamRepository,
) : ViewModel() {

    private val signedOut = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val availableRoles = kotlinx.coroutines.flow.MutableStateFlow<List<TeamRole>>(emptyList())

    val state: StateFlow<SettingsUiState> = combine(
        authRepository.observeActiveInstance(),
        settingsRepository.settings,
        appIconManager.current,
        signedOut,
        kotlinx.coroutines.flow.combine(
            replyTemplateRepository.templates,
            availableRoles,
            ::Pair,
        ),
    ) { instance, settings, appIcon, isSignedOut, templatesAndRoles ->
        SettingsUiState(
            instance = instance,
            settings = settings,
            appIcon = appIcon,
            isSignedOut = isSignedOut,
            templates = templatesAndRoles.first,
            availableRoles = templatesAndRoles.second,
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
        // Die Rollenauswahl braucht die Rollen des Blogs; ohne Plugin bleibt
        // sie leer und der Abschnitt erklaert stattdessen, warum.
        viewModelScope.launch {
            val instanceId = authRepository.observeActiveInstance().first()?.id ?: return@launch
            val outcome = teamRepository.team(instanceId)
            if (outcome is Outcome.Success) availableRoles.value = outcome.value.availableRoles
        }
    }

    fun addTemplate(text: String) {
        viewModelScope.launch { replyTemplateRepository.add(text) }
    }

    fun updateTemplate(id: String, text: String) {
        viewModelScope.launch { replyTemplateRepository.update(id, text) }
    }

    fun removeTemplate(id: String) {
        viewModelScope.launch { replyTemplateRepository.remove(id) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNotificationsEnabled(enabled) }
    }

    fun toggleTeamRole(slug: String) {
        viewModelScope.launch {
            val aktuell = settingsRepository.settings.first().teamRoles
            settingsRepository.setTeamRoles(
                if (slug in aktuell) aktuell - slug else aktuell + slug,
            )
        }
    }

    fun setNotifyScope(scope: NotifyScope) {
        viewModelScope.launch { settingsRepository.setNotifyScope(scope) }
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
