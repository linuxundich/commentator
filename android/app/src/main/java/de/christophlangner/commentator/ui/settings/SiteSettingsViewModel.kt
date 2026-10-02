package de.christophlangner.commentator.ui.settings

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.domain.model.NotifyScope
import de.christophlangner.commentator.domain.model.RoleStyle
import de.christophlangner.commentator.domain.model.TeamRole
import de.christophlangner.commentator.domain.model.WordPressInstance
import de.christophlangner.commentator.domain.repository.AuthRepository
import de.christophlangner.commentator.domain.repository.ReplyTemplate
import de.christophlangner.commentator.domain.repository.ReplyTemplateRepository
import de.christophlangner.commentator.domain.repository.SettingsRepository
import de.christophlangner.commentator.domain.repository.SiteSettings
import de.christophlangner.commentator.domain.repository.TeamRepository
import de.christophlangner.commentator.push.PushSetup
import de.christophlangner.commentator.ui.common.ErrorTexts
import de.christophlangner.commentator.ui.navigation.SiteSettingsRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SiteSettingsUiState(
    val instance: WordPressInstance? = null,
    val settings: SiteSettings = SiteSettings.DEFAULT,
    val templates: List<ReplyTemplate> = emptyList(),
    /**
     * Rollen, die der Blog gemeldet hat.
     *
     * Leer, solange sie nicht ermittelt wurden - ohne das Bridge-Plugin
     * bleibt sie es. Welche Rollen dann zur Auswahl stehen, entscheidet der
     * Bildschirm: Die Namen der Standardrollen sind uebersetzter Text und
     * gehoeren nicht in den Zustand.
     */
    val availableRoles: List<TeamRole> = emptyList(),
    /**
     * Ob der Blog entfernt wurde.
     *
     * [wasLast] sagt, ob es der letzte war - dann gibt es keinen Posteingang
     * mehr, in den man zurückkehren könnte.
     */
    val removed: Boolean = false,
    val wasLast: Boolean = false,
    /** Sofortmeldung: ob eine UnifiedPush-App installiert ist. */
    val pushAvailable: Boolean = false,
    /** Name der gewählten UnifiedPush-App, solange die Sofortmeldung läuft. */
    val pushDistributor: String? = null,
    /** Warum die Sofortmeldung zuletzt nicht eingerichtet werden konnte. */
    val pushError: AppError? = null,
) {
    val canAddTemplate: Boolean get() = templates.size < ReplyTemplate.MAX_TEMPLATES
}

/**
 * Die Einstellungen eines einzelnen Blogs.
 *
 * Welcher gemeint ist, steht in der Route. Bewusst nicht "der aktive Blog":
 * Aus der Liste in den Einstellungen lässt sich auch ein Blog öffnen, der
 * gerade nicht angezeigt wird - und ihn dafür erst zum aktiven zu machen wäre
 * eine Nebenwirkung, die niemand erwartet.
 */
@HiltViewModel
class SiteSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val replyTemplateRepository: ReplyTemplateRepository,
    private val teamRepository: TeamRepository,
    private val pushSetup: PushSetup = PushSetup.NONE,
) : ViewModel() {

    private val instanceId: String = savedStateHandle.toRoute<SiteSettingsRoute>().instanceId

    private val removed = MutableStateFlow(false to false)
    private val availableRoles = MutableStateFlow<List<TeamRole>>(emptyList())
    private val pushAvailable = MutableStateFlow(false)
    private val choiceFailed = MutableStateFlow<AppError?>(null)

    val state: StateFlow<SiteSettingsUiState> = combine(
        authRepository.observeInstances().map { liste ->
            liste.firstOrNull { it.id == instanceId }
        },
        settingsRepository.siteSettings(instanceId),
        replyTemplateRepository.templates(instanceId),
        availableRoles,
        removed,
    ) { instance, settings, templates, roles, (entfernt, warLetzter) ->
        SiteSettingsUiState(
            instance = instance,
            settings = settings,
            templates = templates,
            availableRoles = roles,
            removed = entfernt,
            wasLast = warLetzter,
        )
    }.combine(
        combine(pushAvailable, pushSetup.errors, choiceFailed) { verfuegbar, fehler, auswahl ->
            Triple(verfuegbar, fehler[instanceId] ?: auswahl, pushSetup.currentDistributor())
        },
    ) { zustand, (verfuegbar, fehler, verteiler) ->
        zustand.copy(
            pushAvailable = verfuegbar,
            pushDistributor = verteiler.takeIf { zustand.settings.instantPush },
            pushError = fehler,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SiteSettingsUiState(),
    )

    init {
        // Der Bildschirm zeigt, ob das Bridge-Plugin erkannt wurde. Wird es
        // nachträglich installiert, soll ein Blick in die Einstellungen
        // genügen, damit die App es bemerkt.
        viewModelScope.launch { authRepository.refreshSiteCapabilities(instanceId) }
        // Die Rollenauswahl braucht die Rollen des Blogs; ohne Plugin bleibt
        // sie leer und der Abschnitt erklaert stattdessen, warum.
        viewModelScope.launch {
            val outcome = teamRepository.team(instanceId)
            if (outcome is Outcome.Success) availableRoles.value = outcome.value.availableRoles
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSiteNotificationsEnabled(instanceId, enabled)
        }
    }

    /**
     * Ob eine UnifiedPush-App da ist, ändert sich außerhalb der App. Der
     * Bildschirm fragt deshalb bei jeder Rückkehr nach.
     */
    fun refreshPushAvailability() {
        pushAvailable.value = pushSetup.distributorAvailable()
    }

    /** Nachdem die Oberfläche eine UnifiedPush-App hat wählen lassen. */
    fun onDistributorChosen(chosen: Boolean) {
        if (!chosen) {
            choiceFailed.value = AppError.Unknown(ErrorTexts.PUSH_NO_DISTRIBUTOR)
            return
        }
        choiceFailed.value = null
        viewModelScope.launch { pushSetup.enable(instanceId) }
    }

    fun disableInstantPush() {
        choiceFailed.value = null
        viewModelScope.launch { pushSetup.disable(instanceId) }
    }

    /** Für die Oberfläche, die damit den Auswahldialog öffnet. */
    fun chooseDistributor(activity: Activity) =
        pushSetup.chooseDistributor(activity, ::onDistributorChosen)

    fun setNotifyScope(scope: NotifyScope) {
        viewModelScope.launch { settingsRepository.setNotifyScope(instanceId, scope) }
    }

    fun toggleTeamRole(slug: String) {
        viewModelScope.launch {
            val aktuell = settingsRepository.siteSettings(instanceId).first().teamRoles
            settingsRepository.setTeamRoles(
                instanceId,
                if (slug in aktuell) aktuell - slug else aktuell + slug,
            )
            // Der hinterlegte Stand gilt je Rollenauswahl; nach einer
            // Aenderung muss er neu geholt werden.
            teamRepository.invalidate(instanceId)
        }
    }

    fun setRoleStyle(slug: String, style: RoleStyle) {
        viewModelScope.launch { settingsRepository.setRoleStyle(instanceId, slug, style) }
    }

    fun addTemplate(text: String) {
        viewModelScope.launch { replyTemplateRepository.add(instanceId, text) }
    }

    fun updateTemplate(id: String, text: String) {
        viewModelScope.launch { replyTemplateRepository.update(instanceId, id, text) }
    }

    fun removeTemplate(id: String) {
        viewModelScope.launch { replyTemplateRepository.remove(instanceId, id) }
    }

    fun signOut() {
        viewModelScope.launch {
            // Zuerst, solange die Zugangsdaten noch da sind: Sonst bliebe
            // beim Plugin eine Adresse, die niemand mehr abholt.
            if (state.value.settings.instantPush) pushSetup.disable(instanceId)
            authRepository.signOut(instanceId)
            // Danach gelesen, nicht davor: Ob es der letzte war, entscheidet
            // sich mit dem Entfernen.
            val warLetzter = authRepository.observeInstances().first().isEmpty()
            removed.value = true to warLetzter
        }
    }
}
