package de.christophlangner.commentator.domain.repository

import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.domain.model.Comment
import de.christophlangner.commentator.domain.model.CommentFilter
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
