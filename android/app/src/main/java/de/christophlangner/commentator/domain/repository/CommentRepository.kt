package de.christophlangner.commentator.domain.repository

import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.domain.model.Comment
import de.christophlangner.commentator.domain.model.CommentFilter
import de.christophlangner.commentator.domain.model.EmptyResult
import de.christophlangner.commentator.domain.model.ModerationAction
import de.christophlangner.commentator.domain.model.SyncState
import kotlinx.coroutines.flow.Flow
import java.time.Instant

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

    /** Genau die genannten Kommentare - fuer die Trefferliste einer Suche. */
    fun observeCommentsByIds(instanceId: String, ids: List<Long>): Flow<List<Comment>>

    fun observeReplies(instanceId: String, parentId: Long): Flow<List<Comment>>

    fun observeSyncState(instanceId: String): Flow<SyncState>

    /**
     * Offene Kommentare je Blog, aus dem Zwischenspeicher.
     *
     * Für den Blogumschalter: Er soll zeigen, wo etwas liegt, ohne dafür jeden
     * Blog zu befragen. Blogs ohne Eintrag fehlen in der Abbildung.
     */
    fun observePendingCounts(): Flow<Map<String, Int>>

    /**
     * Die zuletzt bekannten Zahlen je Filter, aus dem Zwischenspeicher.
     *
     * Ein fehlender Eintrag heisst "noch nie geholt", eine 0 heisst "leer".
     * Diese Unterscheidung braucht die Oberflaeche: Ohne sie muesste sie
     * einen leeren Zwischenspeicher als Ladezustand deuten und beim Start
     * Platzhalter zeigen, obwohl die Antwort laengst feststeht.
     */
    fun observeCounts(instanceId: String): Flow<Map<CommentFilter, Int>>

    /** Lädt die erste Seite neu und ersetzt den Cache für diesen Filter. */
    suspend fun refresh(instanceId: String, filter: CommentFilter): Outcome<Unit>

    /** Lädt die nächste Seite nach. Ergebnis: ob weitere Seiten existieren. */
    suspend fun loadNextPage(instanceId: String, filter: CommentFilter): Outcome<Boolean>

    /** Ob für diesen Filter noch weitere Seiten geladen werden können. */
    fun hasMorePages(instanceId: String, filter: CommentFilter): Boolean

    /**
     * Wann dieser Filter zuletzt erfolgreich vom Server geholt wurde, oder
     * `null`, wenn noch nie.
     *
     * Damit kann die Oberfläche beim Umschalten entscheiden, ob sich ein
     * erneuter Abruf überhaupt lohnt.
     */
    fun lastRefreshAt(instanceId: String, filter: CommentFilter): Instant?

    /**
     * Sucht Kommentare auf dem Server und gibt die gefundenen IDs zurueck.
     *
     * Ueber den `search`-Parameter der WordPress-API, und zwar innerhalb des
     * gewaehlten Filters - die Filterleiste behaelt damit ihre Bedeutung.
     * Lokal zu suchen waere schneller, durchsuchte aber nur, was zufaellig
     * im Zwischenspeicher liegt; wer sucht, sucht gerade das, was er nicht
     * vor Augen hat.
     *
     * Die Treffer landen im Zwischenspeicher. Die Oberflaeche beobachtet sie
     * darueber, sodass eine Moderation auch in der Trefferliste sofort wirkt.
     */
    suspend fun search(
        instanceId: String,
        query: String,
        filter: CommentFilter,
    ): Outcome<List<Long>>

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
