package de.christophlangner.commentator.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.domain.repository.AuthRepository
import de.christophlangner.commentator.domain.repository.SiteDiscovery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Schritte der Einrichtung.
 *
 * Getrennt gehalten, weil sich daraus unmittelbar ergibt, was die Oberfläche
 * anzeigen muss - und weil der Fall „diese Installation bietet keinen
 * Autorisierungs-Flow an" eine eigene, erklärbare Situation ist.
 */
enum class SetupStep {
    EnterUrl,
    Authorize,
    ManualCredentials,
}

data class SetupUiState(
    val step: SetupStep = SetupStep.EnterUrl,
    val siteUrlInput: String = "",
    val discovery: SiteDiscovery? = null,
    val username: String = "",
    val applicationPassword: String = "",
    val isBusy: Boolean = false,
    val error: AppError? = null,
    val isDone: Boolean = false,
    /** Hinweis, wenn das Konto zwar gültig ist, aber nicht moderieren darf. */
    val signedInWithoutModerationRights: Boolean = false,
) {
    val canSubmitUrl: Boolean get() = siteUrlInput.isNotBlank() && !isBusy
    val canSubmitCredentials: Boolean
        get() = username.isNotBlank() && applicationPassword.isNotBlank() && !isBusy
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val authCallbackChannel: AuthCallbackChannel,
) : ViewModel() {

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            authCallbackChannel.callbacks.collect { callback ->
                signIn(
                    siteUrl = callback.siteUrl,
                    username = callback.userLogin,
                    applicationPassword = callback.applicationPassword,
                )
            }
        }
        viewModelScope.launch {
            authCallbackChannel.rejections.collect {
                _state.update { it.copy(isBusy = false, error = AppError.Unauthorized) }
            }
        }
    }

    fun onUrlChanged(value: String) {
        _state.update { it.copy(siteUrlInput = value, error = null) }
    }

    fun onUsernameChanged(value: String) {
        _state.update { it.copy(username = value, error = null) }
    }

    fun onApplicationPasswordChanged(value: String) {
        _state.update { it.copy(applicationPassword = value, error = null) }
    }

    fun checkSite() {
        val input = _state.value.siteUrlInput
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, error = null) }

            when (val outcome = authRepository.discoverSite(input)) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        isBusy = false,
                        discovery = outcome.value,
                        siteUrlInput = outcome.value.siteUrl,
                        // Ohne Autorisierungs-Endpunkt bleibt nur die manuelle
                        // Eingabe eines im WordPress-Profil erzeugten Passworts.
                        step = if (outcome.value.authorizationEndpoint != null) {
                            SetupStep.Authorize
                        } else {
                            SetupStep.ManualCredentials
                        },
                    )
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isBusy = false, error = outcome.error)
                }
            }
        }
    }

    /** URL, die im Browser geöffnet wird, oder `null` bei fehlendem Endpunkt. */
    fun authorizationUrl(): String? =
        _state.value.discovery?.let(authRepository::authorizationUrl)

    fun onAuthorizationStarted() {
        _state.update { it.copy(isBusy = true, error = null) }
    }

    fun onAuthorizationAborted() {
        _state.update { it.copy(isBusy = false) }
    }

    fun useManualCredentials() {
        _state.update { it.copy(step = SetupStep.ManualCredentials, error = null, isBusy = false) }
    }

    fun back() {
        _state.update {
            when (it.step) {
                SetupStep.EnterUrl -> it
                SetupStep.Authorize -> it.copy(step = SetupStep.EnterUrl, error = null)
                SetupStep.ManualCredentials -> it.copy(
                    step = if (it.discovery?.authorizationEndpoint != null) {
                        SetupStep.Authorize
                    } else {
                        SetupStep.EnterUrl
                    },
                    error = null,
                )
            }
        }
    }

    fun submitManualCredentials() {
        val current = _state.value
        val siteUrl = current.discovery?.siteUrl ?: current.siteUrlInput
        signIn(siteUrl, current.username, current.applicationPassword)
    }

    private fun signIn(siteUrl: String, username: String, applicationPassword: String) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, error = null) }

            when (val outcome = authRepository.completeSignIn(siteUrl, username, applicationPassword)) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        isBusy = false,
                        isDone = true,
                        // Das Konto wird trotzdem eingerichtet: Der Benutzer
                        // sieht dann seine Kommentare, nur moderieren kann er
                        // nicht. Ein harter Abbruch wäre weniger hilfreich.
                        signedInWithoutModerationRights = !outcome.value.canModerate,
                        applicationPassword = "",
                    )
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isBusy = false, error = outcome.error, applicationPassword = "")
                }
            }
        }
    }
}
