package de.christophlangner.commentator.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import de.christophlangner.commentator.data.local.entity.CommentEntity
import de.christophlangner.commentator.data.local.entity.CommentWithPost
import de.christophlangner.commentator.data.local.entity.NotifiedCommentEntity
import de.christophlangner.commentator.data.local.entity.PostTitleEntity
import de.christophlangner.commentator.data.local.entity.SyncStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommentDao {

    @Transaction
    @Query(
        """
        SELECT c.*, p.title AS postTitle
        FROM comments c
        LEFT JOIN post_titles p ON p.instanceId = c.instanceId AND p.postId = c.postId
        WHERE c.instanceId = :instanceId
          AND (:status IS NULL OR c.status = :status)
        ORDER BY c.dateEpochMillis DESC
        """,
    )
    fun observeComments(instanceId: String, status: String?): Flow<List<CommentWithPost>>

    @Transaction
    @Query(
        """
        SELECT c.*, p.title AS postTitle
        FROM comments c
        LEFT JOIN post_titles p ON p.instanceId = c.instanceId AND p.postId = c.postId
        WHERE c.instanceId = :instanceId AND c.id = :commentId
        """,
    )
    fun observeComment(instanceId: String, commentId: Long): Flow<CommentWithPost?>

    @Transaction
    @Query(
        """
        SELECT c.*, p.title AS postTitle
        FROM comments c
        LEFT JOIN post_titles p ON p.instanceId = c.instanceId AND p.postId = c.postId
        WHERE c.instanceId = :instanceId AND c.parentId = :parentId
        ORDER BY c.dateEpochMillis ASC
        """,
    )
    fun observeReplies(instanceId: String, parentId: Long): Flow<List<CommentWithPost>>

    @Upsert
    suspend fun upsertComments(comments: List<CommentEntity>)

    @Query("DELETE FROM comments WHERE instanceId = :instanceId AND id = :commentId")
    suspend fun deleteComment(instanceId: String, commentId: Long)

    @Query("UPDATE comments SET status = :status WHERE instanceId = :instanceId AND id = :commentId")
    suspend fun updateStatus(instanceId: String, commentId: Long, status: String)

    @Query("SELECT postId FROM comments WHERE instanceId = :instanceId GROUP BY postId")
    suspend fun distinctPostIds(instanceId: String): List<Long>

    @Query("SELECT postId FROM post_titles WHERE instanceId = :instanceId")
    suspend fun knownPostIds(instanceId: String): List<Long>

    @Upsert
    suspend fun upsertPostTitles(titles: List<PostTitleEntity>)

    @Query("SELECT title FROM post_titles WHERE instanceId = :instanceId AND postId = :postId")
    suspend fun postTitle(instanceId: String, postId: Long): String?

    @Query("DELETE FROM comments WHERE instanceId = :instanceId AND status = :status")
    suspend fun deleteByStatus(instanceId: String, status: String)

    @Query("DELETE FROM comments WHERE instanceId = :instanceId")
    suspend fun deleteAllComments(instanceId: String)

    /**
     * Entfernt Einträge, die der Server in diesem Zeitfenster nicht mehr
     * liefert - sie wurden also umgestuft oder gelöscht.
     *
     * Bewusst auf das geladene Zeitfenster begrenzt: Ältere Kommentare, die
     * nur wegen der Seitengröße fehlen, bleiben im Cache erhalten.
     */
    @Query(
        """
        DELETE FROM comments
        WHERE instanceId = :instanceId
          AND (:status IS NULL OR status = :status)
          AND dateEpochMillis >= :oldestKeptEpochMillis
          AND id NOT IN (:keptIds)
        """,
    )
    suspend fun deleteStaleInWindow(
        instanceId: String,
        status: String?,
        oldestKeptEpochMillis: Long,
        keptIds: List<Long>,
    )

    /**
     * Schreibt die erste Seite eines Filters und räumt dabei auf.
     *
     * Kommt eine leere Seite zurück, gibt es zu diesem Status serverseitig
     * nichts mehr - dann wird der Cache für diesen Status vollständig geleert.
     */
    @Transaction
    suspend fun replaceFirstPage(
        instanceId: String,
        status: String?,
        comments: List<CommentEntity>,
    ) {
        if (comments.isEmpty()) {
            if (status == null) deleteAllComments(instanceId) else deleteByStatus(instanceId, status)
            return
        }
        upsertComments(comments)
        deleteStaleInWindow(
            instanceId = instanceId,
            status = status,
            oldestKeptEpochMillis = comments.minOf { it.dateEpochMillis },
            keptIds = comments.map { it.id },
        )
    }

    @Query(
        """
        DELETE FROM comments
        WHERE instanceId = :instanceId
          AND parentId = :parentId
          AND id NOT IN (:keptIds)
        """,
    )
    suspend fun deleteStaleReplies(instanceId: String, parentId: Long, keptIds: List<Long>)

    @Query("DELETE FROM comments WHERE instanceId = :instanceId AND parentId = :parentId")
    suspend fun deleteReplies(instanceId: String, parentId: Long)

    /**
     * Schreibt den Antwortfaden eines Kommentars und raeumt dabei auf.
     *
     * Der Server liefert hier alle Status auf einmal. Deshalb darf - anders
     * als bei der gefilterten Liste - alles geloescht werden, was nicht mehr
     * dabei ist.
     */
    @Transaction
    suspend fun replaceReplies(
        instanceId: String,
        parentId: Long,
        replies: List<CommentEntity>,
    ) {
        if (replies.isEmpty()) {
            deleteReplies(instanceId, parentId)
            return
        }
        upsertComments(replies)
        deleteStaleReplies(instanceId, parentId, replies.map { it.id })
    }

    // --- Synchronisierungszustand ---

    @Query("SELECT * FROM sync_state WHERE instanceId = :instanceId")
    fun observeSyncState(instanceId: String): Flow<SyncStateEntity?>

    @Query("SELECT * FROM sync_state WHERE instanceId = :instanceId")
    suspend fun syncState(instanceId: String): SyncStateEntity?

    @Upsert
    suspend fun upsertSyncState(state: SyncStateEntity)

    // --- Benachrichtigungen ---

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markNotified(entries: List<NotifiedCommentEntity>): List<Long>

    @Query("SELECT commentId FROM notified_comments WHERE instanceId = :instanceId AND commentId IN (:ids)")
    suspend fun alreadyNotified(instanceId: String, ids: List<Long>): List<Long>

    @Query("DELETE FROM notified_comments WHERE notifiedAtEpochMillis < :threshold")
    suspend fun pruneNotified(threshold: Long)

    @Query("DELETE FROM sync_state WHERE instanceId = :instanceId")
    suspend fun deleteSyncState(instanceId: String)

    @Query("DELETE FROM notified_comments WHERE instanceId = :instanceId")
    suspend fun deleteNotified(instanceId: String)

    @Query("DELETE FROM post_titles WHERE instanceId = :instanceId")
    suspend fun deletePostTitles(instanceId: String)
}
