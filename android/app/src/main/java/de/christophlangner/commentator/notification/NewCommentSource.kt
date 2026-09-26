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
    suspend fun fetchUnnotified(instance: WordPressInstance): Outcome<NewComments>

    /**
     * Vermerkt, dass diese Kommentare abgehandelt sind.
     *
     * Dazu gehören auch die stummgeschalteten: Was hier nicht ankommt, gilt
     * beim nächsten Lauf erneut als neu und wird erneut geholt.
     */
    suspend fun markNotified(instance: WordPressInstance, comments: List<Comment>)

    /**
     * Ob für diese Instanz bereits ein Ausgangszustand festgehalten wurde.
     *
     * Beim allerersten Lauf gibt es keinen sinnvollen Vergleichspunkt: Alles,
     * was gerade offen ist, wäre „neu“ und käme als Schwall. Der erste Lauf
     * hält deshalb nur den Stand fest, ohne zu melden.
     *
     * Bewusst Teil dieser Schnittstelle: Vorher las der Hintergrunddienst
     * dafür ein Datenbankfeld aus, das diese Klasse nebenbei beschrieb - ein
     * Vertrag, der nirgends stand und den eine andere Implementierung
     * stillschweigend gebrochen hätte.
     */
    suspend fun hasBaseline(instance: WordPressInstance): Boolean
}

/**
 * Was ein Lauf gefunden hat.
 *
 * Getrennt nach dem, was gemeldet wird, und dem, was der Benutzer für diese
 * Rolle abbestellt hat. Beides wird vermerkt, gemeldet wird nur das erste:
 * Ohne diese Trennung bliebe der Stand hinter den stummen Kommentaren
 * stehen, und jeder Lauf holte sie erneut vom Blog.
 */
data class NewComments(
    val toReport: List<Comment> = emptyList(),
    /** Erkannt, aber auf Wunsch ohne Benachrichtigung. */
    val muted: List<Comment> = emptyList(),
) {
    val all: List<Comment> get() = toReport + muted

    companion object {
        val NONE = NewComments()
    }
}
