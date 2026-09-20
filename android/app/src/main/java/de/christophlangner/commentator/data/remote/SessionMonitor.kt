package de.christophlangner.commentator.data.remote

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Merkt sich, für welche Instanzen der Server die Zugangsdaten abgelehnt hat.
 *
 * Wird vom HTTP-Interceptor gesetzt und von der Oberfläche beobachtet: Bei
 * ungültiger Sitzung werden schreibende Aktionen gesperrt und eine erneute
 * Anmeldung angeboten, statt jede einzelne Aktion mit einem Fehler zu quittieren.
 */
@Singleton
class SessionMonitor @Inject constructor() {

    private val invalidInstances = MutableStateFlow<Set<String>>(emptySet())

    fun reportUnauthorized(instanceId: String) {
        invalidInstances.update { it + instanceId }
    }

    fun reportAuthorized(instanceId: String) {
        invalidInstances.update { it - instanceId }
    }

    fun observeInvalid(instanceId: String): Flow<Boolean> =
        invalidInstances.map { instanceId in it }

    fun isInvalid(instanceId: String): Boolean = instanceId in invalidInstances.value
}
