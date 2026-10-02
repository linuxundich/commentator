package de.christophlangner.commentator.domain.repository

/**
 * Hinweise auf einzelne Kommentare, die außerhalb der App stehen - heute die
 * Benachrichtigungen.
 *
 * Eine Benachrichtigung zu einem Kommentar, der längst erledigt ist, fordert
 * zu etwas auf, das es nicht mehr zu tun gibt. Wer einen Kommentar erledigt,
 * räumt deshalb seinen Hinweis weg. Als Schnittstelle, damit die Use Cases
 * nichts von Android wissen müssen.
 */
interface CommentAlerts {

    fun dismiss(instanceId: String, commentId: Long)

    companion object {
        /** Für Tests und Aufrufer, die keine Hinweise kennen. */
        val NONE: CommentAlerts = object : CommentAlerts {
            override fun dismiss(instanceId: String, commentId: Long) = Unit
        }
    }
}
