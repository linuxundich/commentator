package de.christophlangner.commentator.fake

import de.christophlangner.commentator.data.local.dao.CommentDao
import de.christophlangner.commentator.data.local.entity.CommentEntity
import de.christophlangner.commentator.data.local.entity.CommentWithPost
import de.christophlangner.commentator.data.local.entity.FilterCountEntity
import de.christophlangner.commentator.data.local.entity.InstanceCount
import de.christophlangner.commentator.data.local.entity.NotifiedCommentEntity
import de.christophlangner.commentator.data.local.entity.PostTitleEntity
import de.christophlangner.commentator.data.local.entity.SyncStateEntity
import de.christophlangner.commentator.data.local.entity.TeamMemberEntity
import de.christophlangner.commentator.data.local.entity.TeamRoleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** In-Memory-Ersatz für die Datenbank, ohne Android-Laufzeit. */
class FakeCommentDao : CommentDao {

    val stored = MutableStateFlow<List<CommentEntity>>(emptyList())
    private val titles = MutableStateFlow<List<PostTitleEntity>>(emptyList())
    private val syncStates = MutableStateFlow<Map<String, SyncStateEntity>>(emptyMap())
    val notified = MutableStateFlow<List<NotifiedCommentEntity>>(emptyList())
    private val filterCounts = MutableStateFlow<List<FilterCountEntity>>(emptyList())
    private val teamMembers = MutableStateFlow<List<TeamMemberEntity>>(emptyList())
    private val teamRoles = MutableStateFlow<List<TeamRoleEntity>>(emptyList())

    private fun withTitle(entity: CommentEntity) = CommentWithPost(
        comment = entity,
        postTitle = titles.value
            .firstOrNull { it.instanceId == entity.instanceId && it.postId == entity.postId }
            ?.title,
    )

    override fun observeTeamMembers(instanceId: String): Flow<List<TeamMemberEntity>> =
        teamMembers.map { liste -> liste.filter { it.instanceId == instanceId } }

    override fun observeTeamRoles(instanceId: String): Flow<List<TeamRoleEntity>> =
        teamRoles.map { liste -> liste.filter { it.instanceId == instanceId } }

    override suspend fun teamMembers(instanceId: String): List<TeamMemberEntity> =
        teamMembers.value.filter { it.instanceId == instanceId }

    override suspend fun teamRoles(instanceId: String): List<TeamRoleEntity> =
        teamRoles.value.filter { it.instanceId == instanceId }

    override suspend fun upsertTeamMembers(members: List<TeamMemberEntity>) {
        val neu = members.map { it.instanceId to it.userId }.toSet()
        teamMembers.value =
            teamMembers.value.filterNot { (it.instanceId to it.userId) in neu } + members
    }

    override suspend fun upsertTeamRoles(roles: List<TeamRoleEntity>) {
        val neu = roles.map { it.instanceId to it.slug }.toSet()
        teamRoles.value = teamRoles.value.filterNot { (it.instanceId to it.slug) in neu } + roles
    }

    override suspend fun deleteTeamMembers(instanceId: String) {
        teamMembers.value = teamMembers.value.filterNot { it.instanceId == instanceId }
    }

    override suspend fun deleteTeamRoles(instanceId: String) {
        teamRoles.value = teamRoles.value.filterNot { it.instanceId == instanceId }
    }

    override fun observeFilterCounts(instanceId: String): Flow<List<FilterCountEntity>> =
        filterCounts.map { liste -> liste.filter { it.instanceId == instanceId } }

    override suspend fun upsertFilterCounts(counts: List<FilterCountEntity>) {
        val neu = counts.map { it.instanceId to it.filter }.toSet()
        filterCounts.value =
            filterCounts.value.filterNot { (it.instanceId to it.filter) in neu } + counts
    }

    override suspend fun deleteFilterCounts(instanceId: String) {
        filterCounts.value = filterCounts.value.filterNot { it.instanceId == instanceId }
    }

    override fun observeCountsByInstance(status: String): Flow<List<InstanceCount>> =
        stored.map { list ->
            list.filter { it.status == status }
                .groupingBy { it.instanceId }
                .eachCount()
                .map { (instanceId, anzahl) -> InstanceCount(instanceId, anzahl) }
        }

    override fun observeComments(instanceId: String, status: String?): Flow<List<CommentWithPost>> =
        stored.map { list ->
            list.filter { it.instanceId == instanceId && (status == null || it.status == status) }
                .sortedByDescending { it.dateEpochMillis }
                .map(::withTitle)
        }

    override suspend fun idsOlderThan(
        instanceId: String,
        status: String?,
        beforeEpochMillis: Long,
        limit: Int,
    ): List<Long> = stored.value
        .filter { it.instanceId == instanceId && (status == null || it.status == status) }
        .filter { it.dateEpochMillis < beforeEpochMillis }
        .sortedByDescending { it.dateEpochMillis }
        .take(limit)
        .map { it.id }

    override suspend fun deleteComments(instanceId: String, ids: List<Long>) {
        stored.value = stored.value.filterNot { it.instanceId == instanceId && it.id in ids }
    }

    override fun observeUnanswered(instanceId: String, ownUserId: Long): Flow<List<CommentWithPost>> =
        combine(stored, teamMembers) { list, members ->
            val team = members.filter { it.instanceId == instanceId }.map { it.userId }.toSet() +
                listOfNotNull(ownUserId.takeIf { it != 0L })
            val approved = list.filter { it.instanceId == instanceId && it.status == "APPROVED" }
            approved
                .filter { it.authorId !in team }
                .filter { c -> approved.none { it.parentId == c.id && it.authorId in team } }
                .sortedByDescending { it.dateEpochMillis }
                .map(::withTitle)
        }

    override fun observeComment(instanceId: String, commentId: Long): Flow<CommentWithPost?> =
        stored.map { list ->
            list.firstOrNull { it.instanceId == instanceId && it.id == commentId }?.let(::withTitle)
        }

    override fun observeCommentsByIds(
        instanceId: String,
        ids: List<Long>,
    ): Flow<List<CommentWithPost>> =
        stored.map { list ->
            list.filter { it.instanceId == instanceId && it.id in ids }
                .sortedByDescending { it.dateEpochMillis }
                .map(::withTitle)
        }

    override fun observeReplies(instanceId: String, parentId: Long): Flow<List<CommentWithPost>> =
        stored.map { list ->
            list.filter { it.instanceId == instanceId && it.parentId == parentId }.map(::withTitle)
        }

    override suspend fun knownCommentIds(instanceId: String, ids: List<Long>): List<Long> =
        stored.value.filter { it.instanceId == instanceId && it.id in ids }.map { it.id }

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

    override suspend fun deleteStaleReplies(
        instanceId: String,
        parentId: Long,
        keptIds: List<Long>,
    ) {
        stored.value = stored.value.filterNot {
            it.instanceId == instanceId && it.parentId == parentId && it.id !in keptIds
        }
    }

    override suspend fun deleteReplies(instanceId: String, parentId: Long) {
        stored.value = stored.value.filterNot {
            it.instanceId == instanceId && it.parentId == parentId
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
