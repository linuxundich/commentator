package de.christophlangner.commentator.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.core.net.ConnectivityObserver
import de.christophlangner.commentator.domain.model.Comment
import de.christophlangner.commentator.domain.model.CommentFilter
import de.christophlangner.commentator.domain.model.CommentSignals
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.model.ModerationAction
import de.christophlangner.commentator.domain.model.WordPressInstance
import de.christophlangner.commentator.domain.repository.AuthRepository
import de.christophlangner.commentator.domain.repository.CommentRepository
import de.christophlangner.commentator.domain.repository.SettingsRepository
import de.christophlangner.commentator.domain.usecase.ModerateCommentUseCase
import de.christophlangner.commentator.domain.usecase.UndoModerationUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Zustand des Posteingangs.
 *
 * Die Liste kommt immer aus dem lokalen Cache; [isRefreshing] und [lastSync]
 * sagen, wie aktuell sie ist. Dadurch gibt es keinen Zustand, in dem die
 * Oberfläche leer bleibt, nur weil gerade keine Verbindung besteht.
 */
data class InboxUiState(
    val instance: WordPressInstance? = null,
    val filter: CommentFilter = CommentFilter.PENDING,
    val comments: List<Comment> = emptyList(),
    val isInitialLoad: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val isOffline: Boolean = false,
    val sessionInvalid: Boolean = false,
    val lastSync: Instant? = null,
    val error: AppError? = null,
    val showAvatars: Boolean = false,
    /** Auffaelligkeiten je Kommentar-ID; fehlt ein Eintrag, gibt es nichts zu zeigen. */
    val signals: Map<Long, CommentSignals> = emptyMap(),
    /** Anzahl je Filter. Fehlt ein Eintrag, zeigt die Leiste dort keine Zahl. */
    val counts: Map<CommentFilter, Int> = emptyMap(),
) {
    /** Ohne Verbindung, ohne Berechtigung oder mit ungültiger Sitzung wird nicht moderiert. */
    val moderationEnabled: Boolean
        get() = !isOffline && !sessionInvalid && instance?.canModerate == true
}

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val commentRepository: CommentRepository,
    private val moderateComment: ModerateCommentUseCase,
    private val undoModeration: UndoModerationUseCase,
    settingsRepository: SettingsRepository,
    connectivity: ConnectivityObserver,
) : ViewModel() {

    /** Ereignisse, die genau einmal passieren sollen - etwa eine Snackbar. */
    sealed interface Event {
        data class ModerationDone(
            val commentId: Long,
            val action: ModerationAction,
            val previousStatus: CommentStatus,
            val undoable: Boolean,
        ) : Event

        data class Emptied(val deleted: Int, val remaining: Int) : Event
        data class Failed(val error: AppError) : Event
    }

    private data class Transient(
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val initialLoadDone: Boolean = false,
        val error: AppError? = null,
        /** Anzahl je Filter, leer solange nicht ermittelt. */
        val counts: Map<CommentFilter, Int> = emptyMap(),
    )

    private val filter = MutableStateFlow(CommentFilter.PENDING)
    private val transient = MutableStateFlow(Transient())

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    /**
     * Die aktive Instanz, dauerhaft im Speicher gehalten.
     *
     * Bewusst `Eagerly` und nicht aus [state] abgeleitet: [state] verwendet
     * `WhileSubscribed` und liefert ohne Abonnenten den Anfangswert. Aktionen
     * würden dann still ins Leere laufen, weil keine Instanz bekannt wäre.
     */
    private val instance = authRepository.observeActiveInstance()
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
            initialValue = null,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val comments = combine(instance, filter, ::Pair)
        .flatMapLatest { (instance, filter) ->
            if (instance == null) flowOf(emptyList()) else {
                commentRepository.observeComments(instance.id, filter)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val syncState = instance.flatMapLatest { instance ->
        if (instance == null) flowOf(null) else commentRepository.observeSyncState(instance.id)
    }

    private val environment = combine(
        connectivity.isOnline,
        authRepository.observeSessionInvalid(),
        settingsRepository.settings.map { it.showAvatars }.distinctUntilChanged(),
        syncState,
    ) { isOnline, sessionInvalid, showAvatars, sync ->
        Environment(isOnline, sessionInvalid, showAvatars, sync?.lastSuccessfulSync)
    }

    private data class Environment(
        val isOnline: Boolean,
        val sessionInvalid: Boolean,
        val showAvatars: Boolean,
        val lastSync: Instant?,
    )

    val state: StateFlow<InboxUiState> = combine(
        instance,
        filter,
        comments,
        transient,
        environment,
    ) { instance, filter, comments, transient, environment ->
        InboxUiState(
            instance = instance,
            filter = filter,
            comments = comments,
            isInitialLoad = !transient.initialLoadDone && comments.isEmpty(),
            isRefreshing = transient.isRefreshing,
            isLoadingMore = transient.isLoadingMore,
            canLoadMore = instance != null &&
                comments.isNotEmpty() &&
                commentRepository.hasMorePages(instance.id, filter),
            isOffline = !environment.isOnline,
            sessionInvalid = environment.sessionInvalid,
            lastSync = environment.lastSync,
            error = transient.error,
            showAvatars = environment.showAvatars,
            signals = signalsFor(comments),
            counts = transient.counts,
        )
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
        initialValue = InboxUiState(),
    )

    init {
        // Beim Start und bei jedem Filterwechsel einmal aktualisieren. Die
        // zwischengespeicherte Liste ist währenddessen bereits sichtbar.
        viewModelScope.launch {
            combine(instance, filter, ::Pair)
                .distinctUntilChanged()
                .collect { (instance, _) ->
                    if (instance != null) refresh()
                }
        }
    }

    fun setFilter(newFilter: CommentFilter) {
        filter.value = newFilter
    }

    fun refresh() {
        val instanceId = instance.value?.id ?: return
        viewModelScope.launch {
            transient.update { it.copy(isRefreshing = true, error = null) }
            val outcome = commentRepository.refresh(instanceId, filter.value)
            // Zieht nebenbei nach, was sich am Blog geändert hat - etwa ein
            // nachträglich installiertes Bridge-Plugin. Ein Fehler hier darf
            // die Liste nicht beeinflussen.
            authRepository.refreshSiteCapabilities()
            transient.update {
                it.copy(
                    isRefreshing = false,
                    initialLoadDone = true,
                    error = outcome.errorOrNull,
                )
            }
            loadCounts()
        }
    }

    /**
     * Auffaelligkeiten der geladenen Kommentare.
     *
     * Rein lokal aus dem Cache, ohne zusaetzlichen Abruf. Dubletten lassen
     * sich nur innerhalb des Geladenen erkennen - das genuegt fuer den Fall,
     * um den es geht: derselbe Text mehrfach in derselben Welle.
     */
    /**
     * Holt die Zahlen fuer die Filterleiste.
     *
     * Nach dem Aktualisieren, nicht davor: Die Liste soll nicht darauf
     * warten. Scheitert es, bleiben die bisherigen Zahlen stehen - eine
     * verschwindende Zahl waere irritierender als eine kurz veraltete.
     */
    private suspend fun loadCounts() {
        val instanceId = instance.value?.id ?: return
        val outcome = commentRepository.countsByFilter(instanceId)
        if (outcome is Outcome.Success && outcome.value.isNotEmpty()) {
            transient.update { it.copy(counts = outcome.value) }
        }
    }

    private fun signalsFor(comments: List<Comment>): Map<Long, CommentSignals> {
        if (comments.isEmpty()) return emptyMap()
        val duplicated = CommentSignals.duplicatedIds(comments)
        return comments.associate { comment ->
            comment.id to CommentSignals(
                linkCount = CommentSignals.linkCountOf(comment.contentHtml),
                duplicated = comment.id in duplicated,
            )
        }.filterValues { it.hasAny }
    }

    fun loadMore() {
        val instanceId = instance.value?.id ?: return
        val current = state.value
        if (current.isLoadingMore || !current.canLoadMore) return

        viewModelScope.launch {
            transient.update { it.copy(isLoadingMore = true) }
            val outcome = commentRepository.loadNextPage(instanceId, filter.value)
            transient.update { it.copy(isLoadingMore = false) }
            if (outcome is Outcome.Failure) _events.tryEmit(Event.Failed(outcome.error))
        }
    }

    fun dismissError() {
        transient.update { it.copy(error = null) }
    }

    fun moderate(comment: Comment, action: ModerationAction) {
        val instanceId = instance.value?.id ?: return
        viewModelScope.launch {
            when (val outcome = moderateComment(instanceId, comment.id, action)) {
                is Outcome.Success -> {
                    _events.tryEmit(
                        Event.ModerationDone(
                            commentId = comment.id,
                            action = action,
                            previousStatus = outcome.value.previousStatus,
                            undoable = outcome.value.undoable,
                        ),
                    )
                    // Jede Moderation verschiebt einen Kommentar zwischen zwei
                    // Filtern; ohne das blieben die Zahlen bis zum naechsten
                    // Aktualisieren falsch.
                    loadCounts()
                }

                is Outcome.Failure -> _events.tryEmit(Event.Failed(outcome.error))
            }
        }
    }

    /**
     * Leert Spam oder Papierkorb.
     *
     * Bei vielen Eintraegen arbeitet ein Aufruf nur einen Stapel ab; das
     * Ereignis nennt deshalb auch den Rest, damit die Oberflaeche nicht
     * faelschlich "fertig" meldet.
     */
    fun emptyCurrentFilter() {
        val instanceId = instance.value?.id ?: return
        viewModelScope.launch {
            transient.update { it.copy(isRefreshing = true) }
            val outcome = commentRepository.emptyStatus(instanceId, filter.value)
            transient.update { it.copy(isRefreshing = false) }
            when (outcome) {
                is Outcome.Success -> {
                    _events.tryEmit(Event.Emptied(outcome.value.deleted, outcome.value.remaining))
                    loadCounts()
                }

                is Outcome.Failure -> _events.tryEmit(Event.Failed(outcome.error))
            }
        }
    }

    fun undo(commentId: Long, previousStatus: CommentStatus) {
        val instanceId = instance.value?.id ?: return
        viewModelScope.launch {
            val outcome = undoModeration(instanceId, commentId, previousStatus)
            if (outcome is Outcome.Failure) {
                _events.tryEmit(Event.Failed(outcome.error))
            } else {
                loadCounts()
            }
        }
    }
}
