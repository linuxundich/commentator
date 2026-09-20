package de.christophlangner.commentator.ui.inbox

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.christophlangner.commentator.R
import de.christophlangner.commentator.domain.model.Comment
import de.christophlangner.commentator.domain.model.CommentFilter
import de.christophlangner.commentator.domain.model.ModerationAction
import de.christophlangner.commentator.ui.common.ErrorTexts
import de.christophlangner.commentator.ui.common.EmptyState
import de.christophlangner.commentator.ui.common.ErrorState
import de.christophlangner.commentator.ui.common.LoadingState
import de.christophlangner.commentator.ui.common.OfflineBanner
import de.christophlangner.commentator.ui.common.SessionInvalidBanner

/**
 * Posteingang: die wichtigste Ansicht der App.
 *
 * Filter, Aktualisierung und die häufigsten Moderationsentscheidungen sind
 * ohne Umweg erreichbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onOpenComment: (Comment) -> Unit,
    onOpenSettings: () -> Unit,
    onReauthenticate: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    RequestNotificationPermission()

    LaunchedEffect(viewModel, resources) {
        viewModel.events.collect { event ->
            when (event) {
                is InboxViewModel.Event.ModerationDone -> {
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(event.action.messageRes()),
                        actionLabel = if (event.undoable) {
                            resources.getString(R.string.action_undo)
                        } else {
                            null
                        },
                        duration = SnackbarDuration.Short,
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undo(event.commentId, event.previousStatus)
                    }
                }

                is InboxViewModel.Event.Failed -> {
                    snackbarHostState.showSnackbar(
                        message = ErrorTexts.message(resources, event.error),
                        duration = SnackbarDuration.Long,
                        withDismissAction = true,
                    )
                }
            }
        }
    }

    InboxScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onRefresh = viewModel::refresh,
        onFilterSelected = viewModel::setFilter,
        onOpenComment = onOpenComment,
        onModerate = viewModel::moderate,
        onLoadMore = viewModel::loadMore,
        onOpenSettings = onOpenSettings,
        onReauthenticate = onReauthenticate,
        modifier = modifier,
    )
}

/**
 * Der Posteingang ohne eigenen Zustand.
 *
 * Getrennt vom [InboxScreen], damit die Oberfläche ohne ViewModel und ohne
 * Dependency Injection geprüft werden kann - die UI-Tests setzen genau hier an.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InboxScreenContent(
    state: InboxUiState,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onFilterSelected: (CommentFilter) -> Unit,
    onOpenComment: (Comment) -> Unit,
    onModerate: (Comment, ModerationAction) -> Unit,
    onLoadMore: () -> Unit,
    onOpenSettings: () -> Unit,
    onReauthenticate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(state.instance?.displayName ?: stringResource(R.string.app_name)) },
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !state.isRefreshing,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.action_refresh),
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.action_settings),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            SessionInvalidBanner(
                visible = state.sessionInvalid,
                onReauthenticate = onReauthenticate,
            )
            OfflineBanner(isOffline = state.isOffline, lastSync = state.lastSync)
            FilterRow(
                selected = state.filter,
                onSelect = onFilterSelected,
            )

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                InboxContent(
                    state = state,
                    onOpenComment = onOpenComment,
                    onModerate = onModerate,
                    onLoadMore = onLoadMore,
                    onRetry = onRefresh,
                )
            }
        }
    }
}

@Composable
private fun InboxContent(
    state: InboxUiState,
    onOpenComment: (Comment) -> Unit,
    onModerate: (Comment, ModerationAction) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    val listState = rememberLazyListState()

    // Nachladen, sobald das Ende der Liste in Sicht kommt.
    val shouldLoadMore by remember(state.comments.size, state.canLoadMore) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            state.canLoadMore && lastVisible >= state.comments.size - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    when {
        state.isInitialLoad && state.comments.isEmpty() && state.error == null -> LoadingState()

        state.comments.isEmpty() && state.error != null ->
            ErrorState(
                message = ErrorTexts.message(LocalResources.current, state.error),
                onRetry = onRetry,
            )

        state.comments.isEmpty() -> EmptyState(
            icon = Icons.Default.Email,
            title = stringResource(state.filter.emptyTitleRes()),
            description = stringResource(state.filter.emptyDescriptionRes()),
        )

        else -> LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.comments, key = { it.id }) { comment ->
                CommentCard(
                    comment = comment,
                    showAvatar = state.showAvatars,
                    actionsEnabled = state.moderationEnabled,
                    onOpen = { onOpenComment(comment) },
                    onModerate = { action -> onModerate(comment, action) },
                    onReply = { onOpenComment(comment) },
                )
            }

            if (state.isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    selected: CommentFilter,
    onSelect: (CommentFilter) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(CommentFilter.entries.toList()) { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(filter.labelRes())) },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

/**
 * Fragt die Benachrichtigungsberechtigung ab Android 13 an - einmal je
 * Sitzung und nur, wenn sie noch nicht erteilt ist. Eine Ablehnung führt
 * nicht zu wiederholtem Nachfragen.
 */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    // Hier wird keine Ressource nachgeschlagen, sondern der Berechtigungsstand
    // geprüft - dafür ist der Context der richtige Weg.
    val context = LocalContext.current
    var alreadyAsked by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { },
    )

    LaunchedEffect(alreadyAsked) {
        if (alreadyAsked) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        alreadyAsked = true
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private fun ModerationAction.messageRes(): Int = when (this) {
    ModerationAction.Approve -> R.string.snackbar_approved
    ModerationAction.Hold -> R.string.snackbar_held
    ModerationAction.MarkAsSpam -> R.string.snackbar_spam
    is ModerationAction.Delete ->
        if (permanent) R.string.snackbar_deleted else R.string.snackbar_trashed
}

fun CommentFilter.labelRes(): Int = when (this) {
    CommentFilter.ALL -> R.string.filter_all
    CommentFilter.PENDING -> R.string.filter_pending
    CommentFilter.APPROVED -> R.string.filter_approved
    CommentFilter.SPAM -> R.string.filter_spam
    CommentFilter.TRASH -> R.string.filter_trash
}

private fun CommentFilter.emptyTitleRes(): Int = when (this) {
    CommentFilter.PENDING -> R.string.empty_pending_title
    CommentFilter.SPAM -> R.string.empty_spam_title
    CommentFilter.TRASH -> R.string.empty_trash_title
    else -> R.string.empty_generic_title
}

private fun CommentFilter.emptyDescriptionRes(): Int = when (this) {
    CommentFilter.PENDING -> R.string.empty_pending_description
    else -> R.string.empty_generic_description
}
