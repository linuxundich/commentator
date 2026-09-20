package de.christophlangner.commentator.notification

import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.domain.model.Comment
import de.christophlangner.commentator.domain.model.WordPressInstance

/**
 * Erkennung neuer Kommentare.
 *
 * Diese Schnittstelle ist der Grund, warum die App später ohne Umbau auf
 * echtes Push umgestellt werden kann: Die gesamte Benachrichtigungslogik -
 * Kanäle, Entdopplung, Deep Links - hängt an dieser Abstraktion und nicht an
 * der Art, wie ein neuer Kommentar bekannt wird.
 *
 * Version 1 liefert genau eine Implementierung, [PollingNewCommentSource].
 * Eine `FcmNewCommentSource` würde hier andocken, ohne dass sich sonst etwas
 * ändert. Die Abwägung dazu steht in `docs/architecture.md`.
 */
interface NewCommentSource {

    /**
     * Kommentare, über die noch nicht benachrichtigt wurde - bereits
     * entdoppelt und in aufsteigender zeitlicher Reihenfolge.
     */
    suspend fun fetchUnnotified(instance: WordPressInstance): Outcome<List<Comment>>

    /** Vermerkt, dass über diese Kommentare benachrichtigt wurde. */
    suspend fun markNotified(instance: WordPressInstance, comments: List<Comment>)
}
