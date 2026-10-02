package de.christophlangner.commentator.push

import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import javax.inject.Inject

/**
 * Empfängt, was die UnifiedPush-App meldet, und reicht es an [InstantPush].
 *
 * Der Inhalt einer Nachricht wird nicht ausgewertet: Jede Nachricht heißt
 * „es gibt etwas Neues". Was genau, ermittelt die Prüfung selbst.
 */
@AndroidEntryPoint
class CommentatorPushService : PushService() {

    @Inject lateinit var instantPush: InstantPush

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        scope.launch { instantPush.onNewEndpoint(instance, endpoint.url) }
    }

    override fun onMessage(message: PushMessage, instance: String) {
        instantPush.onMessage()
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        instantPush.onRegistrationFailed(instance)
    }

    override fun onUnregistered(instance: String) {
        scope.launch { instantPush.onUnregistered(instance) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
