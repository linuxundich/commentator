package de.christophlangner.commentator.data.repository

import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.core.map
import de.christophlangner.commentator.data.account.InstanceStore
import de.christophlangner.commentator.data.local.dao.CommentDao
import de.christophlangner.commentator.data.local.entity.CommentEntity
import de.christophlangner.commentator.data.local.entity.FilterCountEntity
import de.christophlangner.commentator.data.local.entity.PostTitleEntity
import de.christophlangner.commentator.data.local.entity.SyncStateEntity
import de.christophlangner.commentator.data.remote.ApiExecutor
import de.christophlangner.commentator.data.remote.WordPressApi
import de.christophlangner.commentator.data.remote.WordPressApiProvider
import de.christophlangner.commentator.data.remote.dto.CreateCommentRequest
import de.christophlangner.commentator.data.remote.dto.BlocklistRequest
import de.christophlangner.commentator.data.remote.dto.PushRequest
import de.christophlangner.commentator.data.remote.dto.EmptyRequest
import de.christophlangner.commentator.data.remote.dto.UpdateCommentRequest
import de.christophlangner.commentator.data.remote.mapper.CommentMapper
import de.christophlangner.commentator.domain.model.Comment
import de.christophlangner.commentator.domain.model.CommentFilter
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.model.EmptyResult
import de.christophlangner.commentator.domain.model.ModerationAction
import de.christophlangner.commentator.domain.model.SyncState
import de.christophlangner.commentator.domain.repository.CommentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room ist die einzige Quelle für das, was die Oberfläche anzeigt.
 *
 * Netzwerkaufrufe schreiben in die Datenbank, nicht in die UI. Dadurch ist
 * der Offline-Fall kein Sonderfall: Ohne Verbindung fehlt lediglich die
 * Aktualisierung, angezeigt wird weiterhin der Cache.
 */
@Singleton
class DefaultCommentRepository @Inject constructor(
    private val dao: CommentDao,
    private val clientFactory: WordPressApiProvider,
    private val instanceStore: InstanceStore,
    private val executor: ApiExecutor,
) : CommentRepository {

    private data class PageState(val nextPage: Int, val totalPages: Int)

    /** Wann ältere Einträge je Blog und Filter zuletzt gegengeprüft wurden. */
    private val lastPrune = ConcurrentHashMap<String, Instant>()

    private val paging = ConcurrentHashMap<String, PageState>()

    /**
     * Wann ein Filter zuletzt vom Server kam.
     *
     * Bewusst nur im Arbeitsspeicher: Nach einem Neustart soll ohnehin
     * einmal frisch geladen werden. Persistieren wuerde nur eine Entscheidung
     * verewigen, die dann falsch waere.
     */
    private val lastRefresh = ConcurrentHashMap<String, Instant>()
    private val syncing = ConcurrentHashMap<String, Boolean>()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeComments(instanceId: String, filter: CommentFilter): Flow<List<Comment>> =
        if (filter == CommentFilter.UNANSWERED) {
            instanceStore.instances
                .map { liste -> liste.firstOrNull { it.id == instanceId }?.userId ?: 0L }
                .distinctUntilChanged()
                .flatMapLatest { eigene -> dao.observeUnanswered(instanceId, eigene) }
        } else {
            dao.observeComments(instanceId, filter.status?.name)
        }.map { rows -> rows.map(CommentMapper::toDomain) }

    override fun observeComment(instanceId: String, commentId: Long): Flow<Comment?> =
        dao.observeComment(instanceId, commentId)
            .map { row -> row?.let(CommentMapper::toDomain) }

    override fun observeCommentsByIds(
        instanceId: String,
        ids: List<Long>,
    ): Flow<List<Comment>> =
        if (ids.isEmpty()) {
            flowOf(emptyList())
        } else {
            dao.observeCommentsByIds(instanceId, ids)
                .map { rows -> rows.map(CommentMapper::toDomain) }
        }

    override fun observeReplies(instanceId: String, parentId: Long): Flow<List<Comment>> =
        dao.observeReplies(instanceId, parentId)
            .map { rows -> rows.map(CommentMapper::toDomain) }

    override fun observeSyncState(instanceId: String): Flow<SyncState> =
        dao.observeSyncState(instanceId).map { state ->
            SyncState(
                lastSuccessfulSync = state?.lastSyncEpochMillis?.let(Instant::ofEpochMilli),
                isSyncing = syncing[instanceId] == true,
            )
        }

    override fun observeCounts(instanceId: String): Flow<Map<CommentFilter, Int>> =
        dao.observeFilterCounts(instanceId).map { zeilen ->
            zeilen.mapNotNull { zeile ->
                // Ein Filter, den es nicht mehr gibt, wird stillschweigend
                // uebergangen - die Zeile schadet nicht und verschwindet beim
                // naechsten Abruf.
                CommentFilter.entries.firstOrNull { it.name == zeile.filter }
                    ?.let { it to zeile.count }
            }.toMap()
        }

    override fun observePendingCounts(): Flow<Map<String, Int>> =
        // In der Tabelle steht der Name der Konstante, nicht der Wert der API.
        dao.observeCountsByInstance(CommentStatus.PENDING.name)
            .map { zeilen -> zeilen.associate { it.instanceId to it.anzahl } }

    override suspend fun refresh(instanceId: String, filter: CommentFilter): Outcome<Unit> {
        val api = apiFor(instanceId) ?: return Outcome.Failure(AppError.Unauthorized)
        syncing[instanceId] = true
        try {
            val result = executor.call {
                api.listComments(
                    status = filter.queryValue,
                    page = 1,
                    perPage = PAGE_SIZE,
                )
            }

            return when (result) {
                is Outcome.Failure -> result
                is Outcome.Success -> {
                    val entities = result.value.body.map { CommentMapper.toEntity(it, instanceId) }
                    dao.replaceFirstPage(instanceId, filter.status?.name, entities)
                    paging[pageKey(instanceId, filter)] =
                        PageState(nextPage = 2, totalPages = result.value.totalPages)
                    resolvePostTitles(instanceId, api, entities)
                    resolveThreadParents(instanceId, api, entities)
                    if (filter == CommentFilter.UNANSWERED) resolveReplies(instanceId, api, entities)
                    pruneOlder(instanceId, api, filter, entities)
                    lastRefresh[pageKey(instanceId, filter)] = Instant.now()
                    recordSuccessfulSync(instanceId)
                    Outcome.Success(Unit)
                }
            }
        } finally {
            syncing[instanceId] = false
        }
    }

    override suspend fun search(
        instanceId: String,
        query: String,
        filter: CommentFilter,
    ): Outcome<List<Long>> {
        val api = apiFor(instanceId) ?: return Outcome.Failure(AppError.Unauthorized)

        val result = executor.call {
            api.listComments(
                status = filter.queryValue,
                page = 1,
                perPage = PAGE_SIZE,
                search = query,
            )
        }

        return when (result) {
            is Outcome.Failure -> result
            is Outcome.Success -> {
                val entities = result.value.body.map { CommentMapper.toEntity(it, instanceId) }
                // Nur einfuegen, nicht aufraeumen: Ein Treffer kann ausserhalb
                // des geladenen Zeitfensters liegen, und die gefilterten Listen
                // duerfen davon nichts verlieren.
                dao.upsertComments(entities)
                resolvePostTitles(instanceId, api, entities)
                Outcome.Success(entities.map { it.id })
            }
        }
    }

    override suspend fun loadNextPage(
        instanceId: String,
        filter: CommentFilter,
    ): Outcome<Boolean> {
        val api = apiFor(instanceId) ?: return Outcome.Failure(AppError.Unauthorized)
        val key = pageKey(instanceId, filter)
        val state = paging[key] ?: PageState(nextPage = 2, totalPages = Int.MAX_VALUE)
        if (state.nextPage > state.totalPages) return Outcome.Success(false)

        val result = executor.call {
            api.listComments(
                status = filter.queryValue,
                page = state.nextPage,
                perPage = PAGE_SIZE,
            )
        }

        return when (result) {
            is Outcome.Failure -> result
            is Outcome.Success -> {
                val entities = result.value.body.map { CommentMapper.toEntity(it, instanceId) }
                dao.upsertComments(entities)
                resolvePostTitles(instanceId, api, entities)
                resolveThreadParents(instanceId, api, entities)
                if (filter == CommentFilter.UNANSWERED) resolveReplies(instanceId, api, entities)
                val totalPages = result.value.totalPages
                paging[key] = PageState(nextPage = state.nextPage + 1, totalPages = totalPages)
                Outcome.Success(state.nextPage < totalPages)
            }
        }
    }

    override fun hasMorePages(instanceId: String, filter: CommentFilter): Boolean {
        val state = paging[pageKey(instanceId, filter)] ?: return true
        return state.nextPage <= state.totalPages
    }

    override suspend fun fetchComment(instanceId: String, commentId: Long): Outcome<Comment> {
        val api = apiFor(instanceId) ?: return Outcome.Failure(AppError.Unauthorized)
        val outcome = executor.call { api.getComment(commentId) }.map { response ->
            val entity = CommentMapper.toEntity(response.body, instanceId)
            dao.upsertComments(listOf(entity))
            resolvePostTitles(instanceId, api, listOf(entity))
            CommentMapper.toDomain(entity, postTitleOf(instanceId, entity.postId))
        }
        if (outcome is Outcome.Success) fetchReplies(instanceId, api, commentId)
        return outcome
    }

    /**
     * Der Antwortfaden haengt nicht am Listenfilter.
     *
     * Wer einen offenen Kommentar oeffnet, soll auch dessen bereits
     * freigeschaltete Antworten sehen - die bringt die gefilterte Liste nie
     * mit. Ein Fehler hier laesst den Kommentar selbst unangetastet: Der
     * Faden fehlt dann, der Kommentar ist trotzdem da.
     */
    private suspend fun fetchReplies(instanceId: String, api: WordPressApi, parentId: Long) {
        val result = executor.call { api.listReplies(parentId = parentId) }
        if (result !is Outcome.Success) return
        val entities = result.value.body.map { CommentMapper.toEntity(it, instanceId) }
        dao.replaceReplies(instanceId, parentId, entities)
        resolvePostTitles(instanceId, api, entities)
    }

    override suspend fun countsByFilter(instanceId: String): Outcome<Map<CommentFilter, Int>> {
        val api = apiFor(instanceId) ?: return Outcome.Failure(AppError.Unauthorized)
        val instance = instanceStore.currentActive()?.takeIf { it.id == instanceId }

        val counts = mutableMapOf<CommentFilter, Int>()

        // Das Plugin liefert die Zahlen auf einmal aus wp_count_comments(),
        // das WordPress ohnehin zwischenspeichert.
        //
        // Ausgenommen "Alle": Fruehe Plugin-Fassungen melden dort
        // total_comments, was Spam mitzaehlt - die Zahl passte dann nicht zur
        // Liste, die bei status=all nur Genehmigtes und Offenes zeigt. Welche
        // Fassung installiert ist, kann die App nicht wissen, deshalb wird
        // dieser eine Wert immer bei der Kern-API geholt.
        if (instance?.hasBridgePlugin == true) {
            val summary = executor.call { api.bridgeSummary() }
            if (summary is Outcome.Success) {
                CommentFilter.entries
                    .filter { it != CommentFilter.ALL && it.countedOnServer }
                    .forEach { filter ->
                        summary.value.body.counts[filter.queryValue]?.let { counts[filter] = it }
                    }
            }
            // Faellt das Plugin aus, bleibt counts leer und es wird gezaehlt
            // wie ohne Plugin.
        }

        for (filter in CommentFilter.entries) {
            if (filter in counts || !filter.countedOnServer) continue
            val result = executor.call {
                api.listComments(
                    status = filter.queryValue,
                    page = 1,
                    perPage = 1,
                )
            }
            // Nur die Kopfzeile zaehlt; ein einzelner Fehlschlag laesst die
            // uebrigen Zahlen unberuehrt.
            if (result is Outcome.Success) counts[filter] = result.value.totalItems
        }

        // Festgehalten wird, was tatsaechlich ermittelt wurde. Ein Filter,
        // dessen Abfrage scheiterte, behaelt seinen bisherigen Eintrag -
        // besser eine kurz veraltete Zahl als gar keine.
        if (counts.isNotEmpty()) {
            val jetzt = System.currentTimeMillis()
            dao.upsertFilterCounts(
                counts.map { (filter, anzahl) ->
                    FilterCountEntity(
                        instanceId = instanceId,
                        filter = filter.name,
                        count = anzahl,
                        updatedAtEpochMillis = jetzt,
                    )
                },
            )
        }
        return Outcome.Success(counts)
    }

    override fun lastRefreshAt(instanceId: String, filter: CommentFilter): Instant? =
        lastRefresh[pageKey(instanceId, filter)]

    override suspend fun emptyStatus(
        instanceId: String,
        filter: CommentFilter,
    ): Outcome<EmptyResult> {
        val status = filter.status
        // Nur Spam und Papierkorb: Alles andere endgueltig zu leeren waere
        // eine Sammelloeschung echter Kommentare - dafuer gibt es die
        // Einzelaktionen.
        if (status != CommentStatus.SPAM && status != CommentStatus.TRASH) {
            return Outcome.Failure(AppError.Unknown("empty_unsupported_filter"))
        }
        val api = apiFor(instanceId) ?: return Outcome.Failure(AppError.Unauthorized)
        val instance = instanceStore.currentActive()?.takeIf { it.id == instanceId }

        if (instance?.hasBridgePlugin == true) {
            val result = executor.call { api.bridgeEmpty(EmptyRequest(filter.queryValue)) }
            if (result is Outcome.Success) {
                dao.deleteByStatus(instanceId, status.name)
                return Outcome.Success(
                    EmptyResult(result.value.body.deleted, result.value.body.remaining),
                )
            }
            // Faellt das Plugin aus, wird geloescht wie ohne.
        }

        return emptyOneByOne(instanceId, api, filter, status)
    }

    /**
     * Rueckfall ohne Plugin: eine Anfrage je Kommentar.
     *
     * Die Kern-API kennt keine Sammelloeschung. Das ist spuerbar teurer,
     * aber besser, als die Funktion nur mit Plugin anzubieten - die
     * wenigsten Blogs werden eines installieren.
     */
    private suspend fun emptyOneByOne(
        instanceId: String,
        api: WordPressApi,
        filter: CommentFilter,
        status: CommentStatus,
    ): Outcome<EmptyResult> {
        val listed = executor.call {
            api.listComments(status = filter.queryValue, page = 1, perPage = EMPTY_BATCH)
        }
        if (listed is Outcome.Failure) return listed

        val ids = (listed as Outcome.Success).value.body.map { it.id }
        var deleted = 0
        for (id in ids) {
            val outcome = executor.call { api.deleteComment(id, force = true) }
            if (outcome is Outcome.Success) {
                dao.deleteComment(instanceId, id)
                deleted++
            }
        }

        val remaining = executor.call {
            api.listComments(status = filter.queryValue, page = 1, perPage = 1)
        }.valueOrNull?.totalItems ?: 0

        return Outcome.Success(EmptyResult(deleted, remaining))
    }

    override suspend fun blockAuthor(instanceId: String, value: String): Outcome<Unit> {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return Outcome.Failure(AppError.Unknown("blocklist_empty_value"))
        val api = apiFor(instanceId) ?: return Outcome.Failure(AppError.Unauthorized)

        return executor.call { api.bridgeBlock(BlocklistRequest(trimmed)) }.map { }
    }

    override suspend fun registerPush(instanceId: String, endpoint: String): Outcome<Unit> {
        val api = apiFor(instanceId) ?: return Outcome.Failure(AppError.Unauthorized)
        return executor.callIgnoringBody { api.bridgePushRegister(PushRequest(endpoint)) }
    }

    override suspend fun testPush(instanceId: String): Outcome<Int> {
        val api = apiFor(instanceId) ?: return Outcome.Failure(AppError.Unauthorized)
        return executor.call { api.bridgePushTest() }.map { it.body.sent }
    }

    override suspend fun unregisterPush(instanceId: String, endpoint: String): Outcome<Unit> {
        val api = apiFor(instanceId) ?: return Outcome.Failure(AppError.Unauthorized)
        return executor.callIgnoringBody { api.bridgePushRemove(endpoint) }
    }

    override suspend fun countApprovedByAuthor(
        instanceId: String,
        authorEmail: String,
        excludeCommentId: Long,
    ): Outcome<Int> {
        val api = apiFor(instanceId) ?: return Outcome.Failure(AppError.Unauthorized)
        return executor.call {
            api.countCommentsOfAuthor(
                authorEmail = authorEmail,
                status = CommentStatus.APPROVED.queryValue,
                exclude = excludeCommentId,
            )
        }.map { it.totalItems }
    }

    override suspend fun moderate(
        instanceId: String,
        commentId: Long,
        action: ModerationAction,
    ): Outcome<Unit> {
        val api = apiFor(instanceId) ?: return Outcome.Failure(AppError.Unauthorized)

        return when (action) {
            is ModerationAction.Delete -> {
                val outcome = executor.callIgnoringBody {
                    api.deleteComment(commentId, force = action.permanent)
                }
                if (outcome is Outcome.Success) {
                    if (action.permanent) {
                        dao.deleteComment(instanceId, commentId)
                    } else {
                        dao.updateStatus(instanceId, commentId, CommentStatus.TRASH.name)
                    }
                }
                outcome
            }

            else -> {
                val target = action.resultingStatus ?: return Outcome.Failure(
                    AppError.Unknown("unsupported_action"),
                )
                applyStatus(api, instanceId, commentId, target)
            }
        }
    }

    override suspend fun restoreStatus(
        instanceId: String,
        commentId: Long,
        previous: CommentStatus,
    ): Outcome<Unit> {
        val api = apiFor(instanceId) ?: return Outcome.Failure(AppError.Unauthorized)
        return applyStatus(api, instanceId, commentId, previous)
    }

    override suspend fun updateContent(
        instanceId: String,
        commentId: Long,
        contentHtml: String,
    ): Outcome<Unit> {
        val api = apiFor(instanceId) ?: return Outcome.Failure(AppError.Unauthorized)
        return executor.call {
            api.updateComment(commentId, UpdateCommentRequest(content = contentHtml))
        }.map { response ->
            dao.upsertComments(listOf(CommentMapper.toEntity(response.body, instanceId)))
        }
    }

    override suspend fun reply(
        instanceId: String,
        postId: Long,
        parentId: Long,
        content: String,
    ): Outcome<Comment> {
        val api = apiFor(instanceId) ?: return Outcome.Failure(AppError.Unauthorized)
        return executor.call {
            api.createComment(
                CreateCommentRequest(
                    post = postId,
                    parent = parentId,
                    content = content,
                    // Eine Antwort des Moderators soll unmittelbar sichtbar sein
                    // und nicht selbst in der Warteschlange landen.
                    status = CommentStatus.APPROVED.writeValue,
                ),
            )
        }.map { response ->
            val entity = CommentMapper.toEntity(response.body, instanceId)
            dao.upsertComments(listOf(entity))
            CommentMapper.toDomain(entity, postTitleOf(instanceId, entity.postId))
        }
    }

    private suspend fun applyStatus(
        api: WordPressApi,
        instanceId: String,
        commentId: Long,
        status: CommentStatus,
    ): Outcome<Unit> = executor.call {
        api.updateComment(commentId, UpdateCommentRequest(status = status.writeValue))
    }.map { response ->
        dao.upsertComments(listOf(CommentMapper.toEntity(response.body, instanceId)))
    }

    /**
     * Holt fehlende Beitragstitel nach.
     *
     * Kommentare können an Beiträgen wie an Seiten hängen, deshalb werden
     * beide Endpunkte befragt - aber nur für Kennungen, die noch nicht im
     * Cache stehen.
     */
    private suspend fun resolvePostTitles(
        instanceId: String,
        api: WordPressApi,
        entities: List<CommentEntity>,
    ) {
        val known = dao.knownPostIds(instanceId).toSet()
        val missing = entities.map { it.postId }.toSet() - known - 0L
        if (missing.isEmpty()) return

        val include = missing.joinToString(",")
        val found = mutableListOf<PostTitleEntity>()

        executor.call { api.listPosts(include, missing.size) }.valueOrNull?.body
            ?.forEach { found += PostTitleEntity(instanceId, it.id, it.title.rendered) }

        val stillMissing = missing - found.map { it.postId }.toSet()
        if (stillMissing.isNotEmpty()) {
            executor.call { api.listPages(stillMissing.joinToString(","), stillMissing.size) }
                .valueOrNull?.body
                ?.forEach { found += PostTitleEntity(instanceId, it.id, it.title.rendered) }
        }

        if (found.isNotEmpty()) dao.upsertPostTitles(found)
    }

    /**
     * Holt die Kommentare nach, auf die geantwortet wurde.
     *
     * Ohne sie bliebe der Gespraechsfaden im Filter "Offen" fast immer leer:
     * Dort stehen die Antworten, waehrend der Kommentar davor meist laengst
     * genehmigt ist und deshalb nicht in der Liste auftaucht.
     *
     * Eine Anfrage mit `status=any`, und nur fuer das, was noch fehlt. Nur
     * eine Stufe weit - der unmittelbare Bezug. Wer den ganzen Faden sehen
     * will, oeffnet den Kommentar.
     */
    private suspend fun resolveThreadParents(
        instanceId: String,
        api: WordPressApi,
        entities: List<CommentEntity>,
    ) {
        val bezuege = entities.map { it.parentId }.toSet() - 0L - entities.map { it.id }.toSet()
        if (bezuege.isEmpty()) return

        val fehlend = bezuege - dao.knownCommentIds(instanceId, bezuege.toList()).toSet()
        if (fehlend.isEmpty()) return

        val ergebnis = executor.call {
            api.listComments(
                status = "any",
                page = 1,
                perPage = fehlend.size,
                include = fehlend.joinToString(","),
            )
        }

        val gefunden = ergebnis.valueOrNull?.body
            ?.map { CommentMapper.toEntity(it, instanceId) }
            .orEmpty()
        if (gefunden.isEmpty()) return

        // Nur einfuegen: Diese Kommentare gehoeren nicht zum Filter und
        // duerfen dessen Liste nicht veraendern.
        dao.upsertComments(gefunden)
        resolvePostTitles(instanceId, api, gefunden)
    }

    override suspend fun currentStatuses(
        instanceId: String,
        ids: List<Long>,
    ): Outcome<Map<Long, CommentStatus>> {
        if (ids.isEmpty()) return Outcome.Success(emptyMap())
        val api = apiFor(instanceId) ?: return Outcome.Failure(AppError.Unauthorized)

        // `any` schließt Spam und Papierkorb ein, anders als `all`.
        return executor.call {
            api.listComments(
                status = "any",
                page = 1,
                perPage = ids.size.coerceAtMost(MAX_PER_PAGE),
                include = ids.take(MAX_PER_PAGE).joinToString(","),
            )
        }.map { response ->
            val entities = response.body.map { CommentMapper.toEntity(it, instanceId) }
            dao.upsertComments(entities)
            entities.associate { it.id to CommentMapper.toDomain(it, null).status }
        }
    }

    /**
     * Prüft zwischengespeicherte Kommentare, die älter sind als die eben
     * geladene erste Seite.
     *
     * Die erste Seite räumt nur in ihrem eigenen Zeitfenster auf. Was älter
     * ist, blieb bisher ungeprüft liegen - auch wenn es im Web längst gelöscht
     * oder umgestuft war. Sichtbar wurde das unter „Unbeantwortet“ und an
     * „Text kommt mehrfach vor“, das solche Leichen mitzählte.
     *
     * Eine Anfrage mit include für höchstens [MAX_PER_PAGE] Einträge: Was der
     * Blog nicht mehr kennt, fliegt hinaus, der Rest wird mit seinem aktuellen
     * Status übernommen. Je Blog und Filter höchstens alle
     * [PRUNE_INTERVAL] - sonst kostete jedes Aktualisieren eine Anfrage mehr.
     * Scheitert sie, bleibt alles, wie es war.
     */
    private suspend fun pruneOlder(
        instanceId: String,
        api: WordPressApi,
        filter: CommentFilter,
        page: List<CommentEntity>,
    ) {
        if (page.isEmpty()) return
        val key = pageKey(instanceId, filter)
        val now = Instant.now()
        if (lastPrune[key]?.let { it.plus(PRUNE_INTERVAL).isAfter(now) } == true) return

        val ids = dao.idsOlderThan(
            instanceId = instanceId,
            status = filter.status?.name,
            beforeEpochMillis = page.minOf { it.dateEpochMillis },
            limit = MAX_PER_PAGE,
        )
        // Ohne Kandidaten kein Zeitstempel: Sonst sperrte ein Abruf ohne
        // ältere Einträge die Prüfung für den nächsten, der welche hat.
        if (ids.isEmpty()) return

        val result = executor.call {
            api.listComments(
                status = "any",
                page = 1,
                perPage = ids.size,
                include = ids.joinToString(","),
            )
        }
        val found = result.valueOrNull?.body ?: return
        val entities = found.map { CommentMapper.toEntity(it, instanceId) }
        if (entities.isNotEmpty()) dao.upsertComments(entities)
        val gone = ids - entities.map { it.id }.toSet()
        if (gone.isNotEmpty()) dao.deleteComments(instanceId, gone.toList())
        lastPrune[key] = now
    }

    /**
     * Holt die freigegebenen Antworten auf die geladenen Kommentare - mit
     * einer Anfrage. Erst damit lässt sich sagen, ob einer schon beantwortet
     * ist; die Antworten stehen sonst oft auf einer anderen Seite.
     */
    private suspend fun resolveReplies(
        instanceId: String,
        api: WordPressApi,
        entities: List<CommentEntity>,
    ) {
        if (entities.isEmpty()) return
        val antworten = executor.call {
            api.listComments(
                status = CommentStatus.APPROVED.queryValue,
                page = 1,
                perPage = MAX_PER_PAGE,
                parents = entities.joinToString(",") { it.id.toString() },
            )
        }.valueOrNull?.body
            ?.map { CommentMapper.toEntity(it, instanceId) }
            .orEmpty()
        if (antworten.isNotEmpty()) dao.upsertComments(antworten)
    }

    private suspend fun postTitleOf(instanceId: String, postId: Long): String? =
        dao.postTitle(instanceId, postId)

    private suspend fun recordSuccessfulSync(instanceId: String) {
        val current = dao.syncState(instanceId)
        dao.upsertSyncState(
            current?.copy(lastSyncEpochMillis = System.currentTimeMillis())
                ?: SyncStateEntity(
                    instanceId = instanceId,
                    lastSyncEpochMillis = System.currentTimeMillis(),
                    lastNotifiedCommentId = 0,
                    lastNotifiedDateEpochMillis = 0,
                ),
        )
    }

    private suspend fun apiFor(instanceId: String): WordPressApi? {
        val instance = instanceStore.instances.first().firstOrNull { it.id == instanceId }
            ?: return null
        return clientFactory.forInstance(instance.id, instance.siteUrl)
    }

    private fun pageKey(instanceId: String, filter: CommentFilter) = "$instanceId/${filter.name}"

    private companion object {
        /** Hoechstzahl je Aufruf des Rueckfalls ohne Plugin. */
        const val EMPTY_BATCH = 100

        const val PAGE_SIZE = 20

        /** Wie oft ältere Einträge eines Filters höchstens gegengeprüft werden. */
        val PRUNE_INTERVAL: java.time.Duration = java.time.Duration.ofMinutes(10)

        /** Obergrenze der WordPress-API für `per_page`. */
        const val MAX_PER_PAGE = 100
    }
}
