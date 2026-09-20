package de.christophlangner.commentator.domain.repository

import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.domain.model.Comment
import de.christophlangner.commentator.domain.model.CommentFilter
import de.christophlangner.commentator.domain.model.EmptyResult
import de.christophlangner.commentator.domain.model.ModerationAction
import de.christophlangner.commentator.domain.model.SyncState
import kotlinx.coroutines.flow.Flow

/**
 * Zugriff auf Kommentare einer Instanz.
 *
 * Lesende Operationen liefern einen [Flow] aus dem lokalen Cache – die UI
 * zeigt also sofort etwas an und aktualisiert sich nach jeder Änderung von
 * selbst. Schreibende Operationen geben ein [Outcome] zurück.
 */
interface CommentRepository {

    fun observeComments(instanceId: String, filter: CommentFilter): Flow<List<Comment>>

    fun observeComment(instanceId: String, commentId: Long): Flow<Comment?>

    fun observeReplies(instanceId: String, parentId: Long): Flow<List<Comment>>

    fun observeSyncState(instanceId: String): Flow<SyncState>

    /** Lädt die erste Seite neu und ersetzt den Cache für diesen Filter. */
    suspend fun refresh(instanceId: String, filter: CommentFilter): Outcome<Unit>

    /** Lädt die nächste Seite nach. Ergebnis: ob weitere Seiten existieren. */
    suspend fun loadNextPage(instanceId: String, filter: CommentFilter): Outcome<Boolean>

    /** Ob für diesen Filter noch weitere Seiten geladen werden können. */
    fun hasMorePages(instanceId: String, filter: CommentFilter): Boolean

    /** Holt einen einzelnen Kommentar frisch vom Server, etwa nach einem Deep Link. */
    suspend fun fetchComment(instanceId: String, commentId: Long): Outcome<Comment>

    /**
     * Wie viele Kommentare dieser Adresse bereits freigeschaltet sind, ohne
     * den angegebenen Kommentar selbst mitzuzaehlen.
     *
     * Beantwortet die Frage, die bei fast jedem offenen Kommentar zuerst
     * kommt: Ist das jemand Bekanntes oder der erste Beitrag ueberhaupt?
     */
    suspend fun countApprovedByAuthor(
        instanceId: String,
        authorEmail: String,
        excludeCommentId: Long,
    ): Outcome<Int>

    /**
     * Anzahl der Kommentare je Filter, fuer die Zahlen an der Filterleiste.
     *
     * Fehlende Eintraege bedeuten „nicht ermittelt" - dann zeigt die
     * Filterleiste schlicht keine Zahl, statt eine falsche Null zu behaupten.
     */
    suspend fun countsByFilter(instanceId: String): Outcome<Map<CommentFilter, Int>>

    /**
     * Leert Spam oder Papierkorb endgueltig.
     *
     * Ergebnis: wie viele geloescht wurden und wie viele noch uebrig sind.
     * Es wird in Stapeln gearbeitet, damit auch einige tausend Eintraege
     * nicht in den Zeitablauf des Servers laufen.
     */
    suspend fun emptyStatus(instanceId: String, filter: CommentFilter): Outcome<EmptyResult>

    /**
     * Traegt einen Wert in WordPress' Sperrliste `disallowed_keys` ein.
     *
     * Braucht das Plugin und ein Konto, das seitenweite Optionen aendern
     * darf. Wirkt auf kuenftige Kommentare, nicht ruekwirkend.
     */
    suspend fun blockAuthor(instanceId: String, value: String): Outcome<Unit>

    suspend fun moderate(
        instanceId: String,
        commentId: Long,
        action: ModerationAction,
    ): Outcome<Unit>

    /** Setzt einen zuvor geänderten Status wieder zurück (Rückgängig-Funktion). */
    suspend fun restoreStatus(
        instanceId: String,
        commentId: Long,
        previous: de.christophlangner.commentator.domain.model.CommentStatus,
    ): Outcome<Unit>

    suspend fun updateContent(
        instanceId: String,
        commentId: Long,
        contentHtml: String,
    ): Outcome<Unit>

    suspend fun reply(
        instanceId: String,
        postId: Long,
        parentId: Long,
        content: String,
    ): Outcome<Comment>
}
