package de.christophlangner.commentator.push

import dagger.hilt.android.AndroidEntryPoint
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import javax.inject.Inject

/**
 * Empfängt, was die UnifiedPush-App meldet, und reicht es an [InstantPush].
 *
 * Der Dienst selbst hält nichts: Der Connector beendet ihn unmittelbar nach
 * der Zustellung. Was länger dauert, läuft in [InstantPush].
 */
@AndroidEntryPoint
class CommentatorPushService : PushService() {

    @Inject lateinit var instantPush: InstantPush

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        instantPush.onNewEndpointAsync(instance, endpoint.url)
    }

    override fun onMessage(message: PushMessage, instance: String) {
        instantPush.onMessage(instance, message.content)
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        instantPush.onRegistrationFailed(instance)
    }

    override fun onUnregistered(instance: String) {
        instantPush.onUnregisteredAsync(instance)
    }
}
