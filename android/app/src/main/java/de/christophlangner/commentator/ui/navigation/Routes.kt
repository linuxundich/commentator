package de.christophlangner.commentator.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Typsichere Navigationsziele.
 *
 * Statt Zeichenketten zusammenzusetzen, prüft der Compiler die Argumente.
 * Der Deep Link der Benachrichtigung verwendet dieselbe Route wie die App
 * selbst - es gibt keinen zweiten Weg in die Detailansicht.
 */
@Serializable
data object SetupRoute

@Serializable
data object InboxRoute

@Serializable
data class CommentDetailRoute(
    val instanceId: String,
    val commentId: Long,
)

@Serializable
data object SettingsRoute

@Serializable
data object AboutRoute

object DeepLinks {
    const val SCHEME = "commentator"

    /** Ergibt `commentator://comment/{instanceId}/{commentId}`. */
    const val COMMENT_BASE_PATH = "$SCHEME://comment"

    const val AUTH_CALLBACK_HOST = "auth-callback"
    const val AUTH_REJECTED_HOST = "auth-rejected"
}
