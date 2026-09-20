package de.christophlangner.commentator.ui.setup

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rückmeldung aus dem Autorisierungs-Flow von WordPress.
 *
 * Das Application Password kommt als Parameter einer Deep-Link-URL zurück.
 * Es wird deshalb bewusst **nicht** als Navigationsargument weitergereicht -
 * dort würde es im Backstack und im gespeicherten Zustand landen. Stattdessen
 * geht es einmalig durch diesen Kanal und existiert danach nur noch
 * verschlüsselt.
 */
@Singleton
class AuthCallbackChannel @Inject constructor() {

    data class Callback(
        val siteUrl: String,
        val userLogin: String,
        val applicationPassword: String,
    ) {
        override fun toString(): String =
            "Callback(siteUrl=$siteUrl, userLogin=$userLogin, applicationPassword=redacted)"
    }

    private val _callbacks = MutableSharedFlow<Callback>(extraBufferCapacity = 1)
    val callbacks: SharedFlow<Callback> = _callbacks.asSharedFlow()

    private val _rejections = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val rejections: SharedFlow<Unit> = _rejections.asSharedFlow()

    fun submit(callback: Callback) {
        _callbacks.tryEmit(callback)
    }

    fun submitRejection() {
        _rejections.tryEmit(Unit)
    }
}
