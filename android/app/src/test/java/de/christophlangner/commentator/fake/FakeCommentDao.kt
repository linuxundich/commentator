package de.christophlangner.commentator.fake

import de.christophlangner.commentator.data.local.dao.CommentDao
import de.christophlangner.commentator.data.local.entity.CommentEntity
import de.christophlangner.commentator.data.local.entity.CommentWithPost
import de.christophlangner.commentator.data.local.entity.NotifiedCommentEntity
import de.christophlangner.commentator.data.local.entity.PostTitleEntity
import de.christophlangner.commentator.data.local.entity.SyncStateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-Memory-Ersatz für die Datenbank, ohne Android-Laufzeit. */
class FakeCommentDao : CommentDao {

    val stored = MutableStateFlow<List<CommentEntity>>(emptyList())
    private val titles = MutableStateFlow<List<PostTitleEntity>>(emptyList())
    private val syncStates = MutableStateFlow<Map<String, SyncStateEntity>>(emptyMap())
    val notified = MutableStateFlow<List<NotifiedCommentEntity>>(emptyList())

    private fun withTitle(entity: CommentEntity) = CommentWithPost(
        comment = entity,
        postTitle = titles.value
            .firstOrNull { it.instanceId == entity.instanceId && it.postId == entity.postId }
            ?.title,
    )

    override fun observeComments(instanceId: String, status: String?): Flow<List<CommentWithPost>> =
        stored.map { list ->
            list.filter { it.instanceId == instanceId && (status == null || it.status == status) }
                .sortedByDescending { it.dateEpochMillis }
                .map(::withTitle)
        }

    override fun observeComment(instanceId: String, commentId: Long): Flow<CommentWithPost?> =
        stored.map { list ->
            list.firstOrNull { it.instanceId == instanceId && it.id == commentId }?.let(::withTitle)
        }

    override fun observeReplies(instanceId: String, parentId: Long): Flow<List<CommentWithPost>> =
        stored.map { list ->
            list.filter { it.instanceId == instanceId && it.parentId == parentId }.map(::withTitle)
        }

    override suspend fun upsertComments(comments: List<CommentEntity>) {
        val keys = comments.map { it.instanceId to it.id }.toSet()
        stored.value = stored.value.filterNot { (it.instanceId to it.id) in keys } + comments
    }

    override suspend fun deleteComment(instanceId: String, commentId: Long) {
        stored.value = stored.value.filterNot { it.instanceId == instanceId && it.id == commentId }
    }

    override suspend fun updateStatus(instanceId: String, commentId: Long, status: String) {
        stored.value = stored.value.map {
            if (it.instanceId == instanceId && it.id == commentId) it.copy(status = status) else it
        }
    }

    override suspend fun distinctPostIds(instanceId: String): List<Long> =
        stored.value.filter { it.instanceId == instanceId }.map { it.postId }.distinct()

    override suspend fun knownPostIds(instanceId: String): List<Long> =
        titles.value.filter { it.instanceId == instanceId }.map { it.postId }

    override suspend fun upsertPostTitles(entries: List<PostTitleEntity>) {
        val keys = entries.map { it.instanceId to it.postId }.toSet()
        titles.value = titles.value.filterNot { (it.instanceId to it.postId) in keys } + entries
    }

    override suspend fun postTitle(instanceId: String, postId: Long): String? =
        titles.value.firstOrNull { it.instanceId == instanceId && it.postId == postId }?.title

    override suspend fun deleteByStatus(instanceId: String, status: String) {
        stored.value = stored.value.filterNot { it.instanceId == instanceId && it.status == status }
    }

    override suspend fun deleteAllComments(instanceId: String) {
        stored.value = stored.value.filterNot { it.instanceId == instanceId }
    }

    override suspend fun deleteStaleInWindow(
        instanceId: String,
        status: String?,
        oldestKeptEpochMillis: Long,
        keptIds: List<Long>,
    ) {
        stored.value = stored.value.filterNot {
            it.instanceId == instanceId &&
                (status == null || it.status == status) &&
                it.dateEpochMillis >= oldestKeptEpochMillis &&
                it.id !in keptIds
        }
    }

    override fun observeSyncState(instanceId: String): Flow<SyncStateEntity?> =
        syncStates.map { it[instanceId] }

    override suspend fun syncState(instanceId: String): SyncStateEntity? = syncStates.value[instanceId]

    override suspend fun upsertSyncState(state: SyncStateEntity) {
        syncStates.value = syncStates.value + (state.instanceId to state)
    }

    override suspend fun markNotified(entries: List<NotifiedCommentEntity>): List<Long> {
        val existing = notified.value.map { it.instanceId to it.commentId }.toSet()
        val fresh = entries.filterNot { (it.instanceId to it.commentId) in existing }
        notified.value = notified.value + fresh
        return fresh.map { it.commentId }
    }

    override suspend fun alreadyNotified(instanceId: String, ids: List<Long>): List<Long> =
        notified.value.filter { it.instanceId == instanceId && it.commentId in ids }
            .map { it.commentId }

    override suspend fun pruneNotified(threshold: Long) {
        notified.value = notified.value.filter { it.notifiedAtEpochMillis >= threshold }
    }

    override suspend fun deleteSyncState(instanceId: String) {
        syncStates.value = syncStates.value - instanceId
    }

    override suspend fun deleteNotified(instanceId: String) {
        notified.value = notified.value.filterNot { it.instanceId == instanceId }
    }

    override suspend fun deletePostTitles(instanceId: String) {
        titles.value = titles.value.filterNot { it.instanceId == instanceId }
    }
}
