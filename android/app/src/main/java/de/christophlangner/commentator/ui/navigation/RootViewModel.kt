package de.christophlangner.commentator.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.christophlangner.commentator.domain.repository.AuthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Entscheidet, ob die App in die Einrichtung oder in den Posteingang startet.
 *
 * `null` bedeutet „noch nicht bekannt" - solange wird nichts angezeigt, damit
 * die Einrichtung nicht für einen Sekundenbruchteil aufblitzt.
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    authRepository: AuthRepository,
) : ViewModel() {

    val hasInstance: StateFlow<Boolean?> = authRepository.observeActiveInstance()
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )
}
