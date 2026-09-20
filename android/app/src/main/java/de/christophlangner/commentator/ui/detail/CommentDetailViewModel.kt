package de.christophlangner.commentator.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.core.net.ConnectivityObserver
import de.christophlangner.commentator.domain.model.Comment
import de.christophlangner.commentator.domain.model.CommentSignals
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.model.ModerationAction
import de.christophlangner.commentator.domain.repository.AuthRepository
import de.christophlangner.commentator.domain.repository.CommentRepository
import de.christophlangner.commentator.domain.repository.ReplyTemplate
import de.christophlangner.commentator.domain.repository.ReplyTemplateRepository
import de.christophlangner.commentator.domain.repository.SettingsRepository
import de.christophlangner.commentator.domain.usecase.ModerateCommentUseCase
import de.christophlangner.commentator.domain.usecase.ReplyToCommentUseCase
import de.christophlangner.commentator.domain.usecase.UndoModerationUseCase
import de.christophlangner.commentator.ui.navigation.CommentDetailRoute
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CommentDetailUiState(
    val comment: Comment? = null,
    val replies: List<Comment> = emptyList(),
    val signals: CommentSignals = CommentSignals(),
    /** Gespeicherte Antworttexte fuer die Leiste ueber dem Antwortfeld. */
    val templates: List<ReplyTemplate> = emptyList(),
    /** Bisher freigeschaltete Kommentare dieser Adresse, `null` solange unbekannt. */
    val approvedByAuthor: Int? = null,
    val isLoading: Boolean = true,
    val isSendingReply: Boolean = false,
    val isSavingEdit: Boolean = false,
    val showAuthorEmail: Boolean = false,
    val showAvatars: Boolean = false,
    val isOffline: Boolean = false,
    val sessionInvalid: Boolean = false,
    val canModerate: Boolean = false,
    val error: AppError? = null,
) {
    val actionsEnabled: Boolean get() = !isOffline && !sessionInvalid && canModerate
}

@HiltViewModel
class CommentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val commentRepository: CommentRepository,
    private val moderateComment: ModerateCommentUseCase,
    replyTemplateRepository: ReplyTemplateRepository,
    private val undoModeration: UndoModerationUseCase,
    private val replyToComment: ReplyToCommentUseCase,
    authRepository: AuthRepository,
    settingsRepository: SettingsRepository,
    connectivity: ConnectivityObserver,
) : ViewModel() {

    sealed interface Event {
        data class ModerationDone(
            val commentId: Long,
            val action: ModerationAction,
            val previousStatus: CommentStatus,
            val undoable: Boolean,
        ) : Event

        data object ReplyPublished : Event
        data object EditSaved : Event
        data object CommentGone : Event
        data class Failed(val error: AppError) : Event
    }

    private val route: CommentDetailRoute = savedStateHandle.toRoute()
    val instanceId: String = route.instanceId
    val commentId: Long = route.commentId

    private val busy = MutableStateFlow(BusyState())
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private val authorHistory = MutableStateFlow<Int?>(null)

    /** Zusammengefasst, weil `combine` nur eine begrenzte Stelligkeit hat. */
    private data class Environment(
        val isOnline: Boolean,
        val sessionInvalid: Boolean,
        val canModerate: Boolean,
        val approvedByAuthor: Int?,
        val templates: List<ReplyTemplate>,
    )

    private data class BusyState(
        val isLoading: Boolean = true,
        val isSendingReply: Boolean = false,
        val isSavingEdit: Boolean = false,
        val error: AppError? = null,
    )

    val state: StateFlow<CommentDetailUiState> = combine(
        commentRepository.observeComment(instanceId, commentId),
        commentRepository.observeReplies(instanceId, commentId),
        busy,
        settingsRepository.settings,
        combine(
            connectivity.isOnline,
            authRepository.observeSessionInvalid(),
            authRepository.observeActiveInstance().map { it?.canModerate == true },
            authorHistory,
            replyTemplateRepository.templates,
            ::Environment,
        ),
    ) { comment, replies, busy, settings, environment ->
        val (isOnline, sessionInvalid, canModerate, approvedByAuthor) = environment
        CommentDetailUiState(
            comment = comment,
            replies = replies,
            signals = comment?.let {
                CommentSignals(
                    linkCount = CommentSignals.linkCountOf(it.contentHtml),
                    firstTimeAuthor = approvedByAuthor?.let { count -> count == 0 },
                )
            } ?: CommentSignals(),
            approvedByAuthor = approvedByAuthor,
            templates = environment.templates,
            isLoading = busy.isLoading && comment == null,
            isSendingReply = busy.isSendingReply,
            isSavingEdit = busy.isSavingEdit,
            showAuthorEmail = settings.showAuthorEmail,
            showAvatars = settings.showAvatars,
            isOffline = !isOnline,
            sessionInvalid = sessionInvalid,
            canModerate = canModerate,
            error = busy.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
        initialValue = CommentDetailUiState(),
    )

    init {
        refresh()
    }

    /**
     * Holt den Kommentar frisch vom Server.
     *
     * Wichtig für den Einstieg über eine Benachrichtigung: Dort ist unklar,
     * ob der Kommentar überhaupt schon im Cache liegt.
     */
    fun refresh() {
        viewModelScope.launch {
            busy.update { it.copy(isLoading = true, error = null) }
            val outcome = commentRepository.fetchComment(instanceId, commentId)
            busy.update { it.copy(isLoading = false, error = outcome.errorOrNull) }

            if (outcome is Outcome.Failure &&
                (outcome.error == AppError.NotFound ||
                    (outcome.error as? AppError.WordPress)?.code == "rest_comment_invalid_id")
            ) {
                _events.tryEmit(Event.CommentGone)
                return@launch
            }

            (outcome as? Outcome.Success)?.let { loadAuthorHistory(it.value) }
        }
    }

    /**
     * Zaehlt die bisher freigeschalteten Kommentare dieser Adresse.
     *
     * Ein eigener, sehr kleiner Abruf: Ausgewertet wird nur eine Kopfzeile.
     * Scheitert er, bleibt der Hinweis einfach aus - er ist eine Hilfe, keine
     * Voraussetzung fuer die Moderation.
     *
     * Der Kommentar kommt aus dem Abruf und nicht aus `state`: Solange die
     * Oberflaeche den Zustand noch nicht beobachtet, steht dort nichts.
     */
    private suspend fun loadAuthorHistory(comment: Comment) {
        val email = comment.authorEmail?.takeIf { it.isNotBlank() } ?: return
        val outcome = commentRepository.countApprovedByAuthor(
            instanceId = instanceId,
            authorEmail = email,
            excludeCommentId = commentId,
        )
        authorHistory.value = outcome.valueOrNull
    }

    fun moderate(action: ModerationAction) {
        viewModelScope.launch {
            when (val outcome = moderateComment(instanceId, commentId, action)) {
                is Outcome.Success -> _events.tryEmit(
                    Event.ModerationDone(
                        commentId = commentId,
                        action = action,
                        previousStatus = outcome.value.previousStatus,
                        undoable = outcome.value.undoable,
                    ),
                )

                is Outcome.Failure -> _events.tryEmit(Event.Failed(outcome.error))
            }
        }
    }

    fun undo(previousStatus: CommentStatus) {
        viewModelScope.launch {
            val outcome = undoModeration(instanceId, commentId, previousStatus)
            if (outcome is Outcome.Failure) _events.tryEmit(Event.Failed(outcome.error))
        }
    }

    fun sendReply(text: String) {
        val parent = state.value.comment ?: return
        viewModelScope.launch {
            busy.update { it.copy(isSendingReply = true) }
            val outcome = replyToComment(instanceId, parent, text)
            busy.update { it.copy(isSendingReply = false) }

            when (outcome) {
                is Outcome.Success -> {
                    _events.tryEmit(Event.ReplyPublished)
                    // Der Thread wird neu geladen, damit die Antwort dort
                    // auftaucht, wo WordPress sie tatsächlich einsortiert hat.
                    commentRepository.fetchComment(instanceId, commentId)
                }

                is Outcome.Failure -> _events.tryEmit(Event.Failed(outcome.error))
            }
        }
    }

    fun saveEdit(contentHtml: String) {
        viewModelScope.launch {
            busy.update { it.copy(isSavingEdit = true) }
            val outcome = commentRepository.updateContent(instanceId, commentId, contentHtml)
            busy.update { it.copy(isSavingEdit = false) }

            when (outcome) {
                is Outcome.Success -> _events.tryEmit(Event.EditSaved)
                is Outcome.Failure -> _events.tryEmit(Event.Failed(outcome.error))
            }
        }
    }
}
