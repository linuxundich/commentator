package de.christophlangner.commentator.ui.navigation

import android.net.Uri
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

/** Die Einstellungen eines einzelnen Blogs. */
@Serializable
data class SiteSettingsRoute(val instanceId: String)

@Serializable
data object AboutRoute

object DeepLinks {
    const val SCHEME = "commentator"

    /** Ergibt `commentator://comment/{instanceId}/{commentId}`. */
    const val COMMENT_BASE_PATH = "$SCHEME://comment"

    const val COMMENT_HOST = "comment"

    /**
     * `commentator://inbox/{instanceId}` - der Posteingang eines Blogs.
     *
     * Bewusst keine Navigationsroute: Der Posteingang ist der Startpunkt und
     * zeigt immer den aktiven Blog. Dieser Link wechselt ihn, statt ein
     * zweites Ziel daneben zu stellen.
     */
    const val INBOX_HOST = "inbox"

    const val AUTH_CALLBACK_HOST = "auth-callback"
    const val AUTH_REJECTED_HOST = "auth-rejected"

    fun inboxPath(instanceId: String) = "$SCHEME://$INBOX_HOST/$instanceId"

    /** Ob dieser Link in die Oberfläche führt - im Gegensatz zum Anmelde-Rücksprung. */
    fun isScreenLink(uri: Uri?): Boolean =
        uri?.scheme == SCHEME && (uri.host == COMMENT_HOST || uri.host == INBOX_HOST)

    fun isComment(uri: Uri?): Boolean = uri?.scheme == SCHEME && uri.host == COMMENT_HOST

    /**
     * Der Blog, den dieser Link betrifft.
     *
     * Beide Links nennen ihn als ersten Pfadabschnitt. Ohne diesen Wechsel
     * zeigte der Posteingang hinter einer angetippten Benachrichtigung den
     * vorher gewählten Blog - und damit nicht den Kommentar, von dem die
     * Meldung sprach.
     */
    fun instanceIdOf(uri: Uri?): String? {
        if (!isScreenLink(uri)) return null
        return uri?.pathSegments?.firstOrNull()?.takeIf { it.isNotBlank() }
    }
}
