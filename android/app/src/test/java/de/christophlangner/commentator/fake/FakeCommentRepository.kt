package de.christophlangner.commentator.fake

import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.domain.model.Comment
import de.christophlangner.commentator.domain.model.CommentFilter
import de.christophlangner.commentator.domain.model.EmptyResult
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.model.ModerationAction
import de.christophlangner.commentator.domain.model.SyncState
import de.christophlangner.commentator.domain.repository.CommentRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Handgeschriebener Doppelgänger statt eines Mocking-Rahmenwerks.
 *
 * Er verhält sich wie die echte Implementierung in dem, worauf es ankommt:
 * Die Liste kommt aus einem beobachtbaren Zustand, und schreibende Aktionen
 * verändern genau diesen Zustand.
 */
class FakeCommentRepository : CommentRepository {

    val comments = MutableStateFlow<List<Comment>>(emptyList())
    private val syncState = MutableStateFlow(SyncState(null, false))

    var refreshResult: Outcome<Unit> = Outcome.Success(Unit)
    var moderateResult: Outcome<Unit> = Outcome.Success(Unit)
    var replyResult: (() -> Outcome<Comment>)? = null
    var nextPageResult: Outcome<Boolean> = Outcome.Success(false)
    var morePages: Boolean = false

    var refreshCount: Int = 0
        private set

    /** Was die Suche liefert; ohne Vorgabe wird lokal nach dem Text gesucht. */
    var searchResult: Outcome<List<Long>>? = null
    val searches = mutableListOf<Pair<String, CommentFilter>>()
    val moderated = mutableListOf<Pair<Long, ModerationAction>>()
    val restored = mutableListOf<Pair<Long, CommentStatus>>()

    override fun observeComments(instanceId: String, filter: CommentFilter): Flow<List<Comment>> =
        comments.map { list ->
            // Auch nach Blog: Die Liste eines Blogs darf nicht die Kommentare
            // eines anderen enthalten.
            val eigene = list.filter { it.instanceId == instanceId }
            val status = filter.status ?: return@map eigene
            eigene.filter { it.status == status }
        }

    override fun observePendingCounts(): Flow<Map<String, Int>> =
        comments.map { list ->
            list.filter { it.status == CommentStatus.PENDING }
                .groupingBy { it.instanceId }
                .eachCount()
        }

    override fun observeComment(instanceId: String, commentId: Long): Flow<Comment?> =
        comments.map { list -> list.firstOrNull { it.id == commentId } }

    override fun observeCommentsByIds(
        instanceId: String,
        ids: List<Long>,
    ): Flow<List<Comment>> =
        comments.map { list -> list.filter { it.id in ids } }

    override fun observeReplies(instanceId: String, parentId: Long): Flow<List<Comment>> =
        comments.map { list -> list.filter { it.parentId == parentId } }

    override fun observeSyncState(instanceId: String): Flow<SyncState> = syncState

    override suspend fun search(
        instanceId: String,
        query: String,
        filter: CommentFilter,
    ): Outcome<List<Long>> {
        searches += query to filter
        searchResult?.let { return it }
        val treffer = comments.value
            .filter { it.contentPlain.contains(query, ignoreCase = true) ||
                it.authorName.contains(query, ignoreCase = true) }
            .filter { filter.status == null || it.status == filter.status }
            .map { it.id }
        return Outcome.Success(treffer)
    }

    /**
     * Haelt den Abruf an, bis der Test ihn freigibt.
     *
     * Damit laesst sich pruefen, was waehrend des Ladens auf dem Schirm
     * steht - und genau darum geht es beim Aufbau.
     */
    var refreshGate: CompletableDeferred<Unit>? = null

    override suspend fun refresh(instanceId: String, filter: CommentFilter): Outcome<Unit> {
        refreshCount++
        refreshGate?.await()
        if (refreshResult is Outcome.Success) {
            syncState.value = SyncState(Instant.ofEpochMilli(1_000), false)
        }
        return refreshResult
    }

    override suspend fun loadNextPage(
        instanceId: String,
        filter: CommentFilter,
    ): Outcome<Boolean> = nextPageResult

    override fun hasMorePages(instanceId: String, filter: CommentFilter): Boolean = morePages

    /** Was `lastRefreshAt` meldet; `null` heisst "noch nie geholt". */
    var lastRefreshAt: Instant? = null

    override fun lastRefreshAt(instanceId: String, filter: CommentFilter): Instant? = lastRefreshAt

    /** Was eine Sammelloeschung meldet. */
    var emptyResult: EmptyResult = EmptyResult(deleted = 0, remaining = 0)
    val emptied = mutableListOf<CommentFilter>()
    val blocked = mutableListOf<String>()

    override suspend fun emptyStatus(
        instanceId: String,
        filter: CommentFilter,
    ): Outcome<EmptyResult> {
        emptied += filter
        return Outcome.Success(emptyResult)
    }

    override suspend fun blockAuthor(instanceId: String, value: String): Outcome<Unit> {
        blocked += value
        return Outcome.Success(Unit)
    }

    /** Anzahl je Filter, die die Zaehlung liefert. */
    var countsByFilter: Map<CommentFilter, Int> = emptyMap()
    var countCalls = 0

    /**
     * Die gespeicherten Zahlen, wie sie die Oberflaeche liest.
     *
     * Vorbelegt heisst: Diese Zahlen lagen schon vor dem Start im
     * Zwischenspeicher - genau der Fall, um den es beim Aufbau geht.
     */
    val gespeicherteZahlen = MutableStateFlow<Map<CommentFilter, Int>>(emptyMap())

    override fun observeCounts(instanceId: String): Flow<Map<CommentFilter, Int>> =
        gespeicherteZahlen

    override suspend fun countsByFilter(instanceId: String): Outcome<Map<CommentFilter, Int>> {
        countCalls++
        // Wie im echten Repository: Der Abruf schreibt in den
        // Zwischenspeicher, und von dort liest die Oberflaeche.
        if (countsByFilter.isNotEmpty()) gespeicherteZahlen.value = countsByFilter
        return Outcome.Success(countsByFilter)
    }

    /** Bisher freigeschaltete Kommentare je Adresse. */
    val approvedByAuthor = mutableMapOf<String, Int>()

    override suspend fun countApprovedByAuthor(
        instanceId: String,
        authorEmail: String,
        excludeCommentId: Long,
    ): Outcome<Int> = Outcome.Success(approvedByAuthor[authorEmail] ?: 0)

    override suspend fun fetchComment(instanceId: String, commentId: Long): Outcome<Comment> =
        comments.value.firstOrNull { it.id == commentId }
            ?.let { Outcome.Success(it) }
            ?: Outcome.Failure(AppError.NotFound)

    override suspend fun moderate(
        instanceId: String,
        commentId: Long,
        action: ModerationAction,
    ): Outcome<Unit> {
        moderated += commentId to action
        if (moderateResult is Outcome.Success) applyStatus(commentId, action.resultingStatus)
        return moderateResult
    }

    override suspend fun restoreStatus(
        instanceId: String,
        commentId: Long,
        previous: CommentStatus,
    ): Outcome<Unit> {
        restored += commentId to previous
        applyStatus(commentId, previous)
        return Outcome.Success(Unit)
    }

    override suspend fun updateContent(
        instanceId: String,
        commentId: Long,
        contentHtml: String,
    ): Outcome<Unit> {
        comments.value = comments.value.map {
            if (it.id == commentId) it.copy(contentHtml = contentHtml) else it
        }
        return Outcome.Success(Unit)
    }

    override suspend fun reply(
        instanceId: String,
        postId: Long,
        parentId: Long,
        content: String,
    ): Outcome<Comment> = replyResult?.invoke() ?: Outcome.Success(
        testComment(id = 999, parentId = parentId, content = content),
    )

    private fun applyStatus(commentId: Long, status: CommentStatus?) {
        comments.value = if (status == null) {
            comments.value.filterNot { it.id == commentId }
        } else {
            comments.value.map { if (it.id == commentId) it.copy(status = status) else it }
        }
    }
}

fun testComment(
    id: Long,
    authorId: Long = 0,
    instanceId: String = "instance-1",
    status: CommentStatus = CommentStatus.PENDING,
    author: String = "Max Mustermann",
    content: String = "Ein Kommentar",
    parentId: Long = 0,
    postId: Long = 1,
    date: Instant = Instant.ofEpochMilli(1_700_000_000_000),
) = Comment(
    id = id,
    instanceId = instanceId,
    postId = postId,
    parentId = parentId,
    authorId = authorId,
    authorName = author,
    authorEmail = "max@example.test",
    authorUrl = null,
    avatarUrl = null,
    contentHtml = "<p>$content</p>",
    contentPlain = content,
    date = date,
    status = status,
    postTitle = "Linux auf dem Desktop",
    link = null,
)
