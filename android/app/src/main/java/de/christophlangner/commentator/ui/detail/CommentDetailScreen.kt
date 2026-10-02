package de.christophlangner.commentator.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.christophlangner.commentator.R
import de.christophlangner.commentator.core.time.RelativeTime
import de.christophlangner.commentator.domain.model.Comment
import de.christophlangner.commentator.domain.model.CommentSignals
import de.christophlangner.commentator.domain.repository.ReplyTemplate
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.model.ModerationAction
import de.christophlangner.commentator.ui.common.ErrorTexts
import de.christophlangner.commentator.ui.common.LoadingState
import de.christophlangner.commentator.ui.common.OfflineBanner
import de.christophlangner.commentator.ui.common.CommentAvatar
import de.christophlangner.commentator.ui.common.SignalChips
import de.christophlangner.commentator.ui.common.StatusChip
import de.christophlangner.commentator.ui.common.rememberLabelWidth

/**
 * Detailansicht eines Kommentars.
 *
 * Erreichbar aus der Liste und direkt aus einer Benachrichtigung. Zeigt den
 * vollständigen Text, den Zusammenhang und alle Moderationsmöglichkeiten.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentDetailScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CommentDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    var replyText by rememberSaveable { mutableStateOf("") }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showBlockDialog by rememberSaveable { mutableStateOf(false) }
    var editingText by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel, resources) {
        viewModel.events.collect { event ->
            when (event) {
                is CommentDetailViewModel.Event.ModerationDone -> {
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
                        viewModel.undo(event.previousStatus)
                    }
                }

                CommentDetailViewModel.Event.ReplyPublished -> {
                    replyText = ""
                    snackbarHostState.showSnackbar(
                        resources.getString(R.string.snackbar_reply_published),
                    )
                }

                CommentDetailViewModel.Event.AuthorBlocked -> {
                    snackbarHostState.showSnackbar(
                        message = resources.getString(R.string.block_done),
                        duration = SnackbarDuration.Short,
                        withDismissAction = true,
                    )
                }

                CommentDetailViewModel.Event.EditSaved -> {
                    editingText = null
                    snackbarHostState.showSnackbar(resources.getString(R.string.snackbar_edit_saved))
                }

                CommentDetailViewModel.Event.CommentGone -> onNavigateUp()

                is CommentDetailViewModel.Event.Failed -> snackbarHostState.showSnackbar(
                    message = ErrorTexts.message(resources, event.error),
                    duration = SnackbarDuration.Long,
                    withDismissAction = true,
                )
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    OverflowMenu(
                        enabled = state.actionsEnabled,
                        canBlockAuthor = state.canBlockAuthor,
                        onEdit = { editingText = state.comment?.contentHtml.orEmpty() },
                        onBlockAuthor = { showBlockDialog = true },
                        onDeletePermanently = { showDeleteDialog = true },
                    )
                },
            )
        },
    ) { innerPadding ->
        CommentDetailBody(
            state = state,
            replyText = replyText,
            onReplyTextChange = { replyText = it },
            onModerate = viewModel::moderate,
            onSendReply = { viewModel.sendReply(replyText) },
            modifier = Modifier.padding(innerPadding),
        )
    }

    if (showBlockDialog) {
        val email = state.comment?.authorEmail.orEmpty()
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = { Text(stringResource(R.string.dialog_block_title)) },
            text = { Text(stringResource(R.string.dialog_block_message, email)) },
            confirmButton = {
                TextButton(onClick = {
                    showBlockDialog = false
                    viewModel.blockAuthor()
                }) {
                    Text(stringResource(R.string.action_block_author))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showDeleteDialog) {
        ConfirmPermanentDeleteDialog(
            onConfirm = {
                showDeleteDialog = false
                viewModel.moderate(ModerationAction.Delete(permanent = true))
                onNavigateUp()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }

    editingText?.let { text ->
        EditCommentDialog(
            initialText = text,
            isSaving = state.isSavingEdit,
            onSave = viewModel::saveEdit,
            onDismiss = { editingText = null },
        )
    }
}

/**
 * Inhalt der Detailansicht ohne eigenen Zustand.
 *
 * Getrennt vom [CommentDetailScreen], damit Darstellung, Moderationsaktionen
 * und das Antwortfeld ohne ViewModel geprüft werden können.
 */
@Composable
internal fun CommentDetailBody(
    state: CommentDetailUiState,
    replyText: String,
    onReplyTextChange: (String) -> Unit,
    onModerate: (ModerationAction) -> Unit,
    onSendReply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        OfflineBanner(isOffline = state.isOffline, lastSync = null)

        val comment = state.comment
        when {
            comment == null && state.isLoading -> LoadingState()

            comment == null -> Text(
                text = stringResource(R.string.detail_not_available),
                modifier = Modifier.padding(24.dp),
            )

            else -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    CommentHeader(
                        comment = comment,
                        showEmail = state.showAuthorEmail,
                        showAvatar = state.showAvatars,
                    )

                    SignalChips(
                        signals = state.signals,
                        approvedByAuthor = state.approvedByAuthor,
                        modifier = Modifier.padding(top = 8.dp),
                    )

                    Text(
                        text = AnnotatedString.fromHtml(comment.contentHtml),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )

                    ModerationActions(
                        status = comment.status,
                        enabled = state.actionsEnabled,
                        onModerate = onModerate,
                    )

                    if (state.replies.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                        Text(
                            text = stringResource(R.string.detail_replies),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        state.replies.forEach { reply ->
                            ReplyItem(reply, modifier = Modifier.padding(top = 12.dp))
                        }
                    }

                    Spacer(Modifier.padding(bottom = 16.dp))
                }

                ReplyComposer(
                    text = replyText,
                    onTextChange = onReplyTextChange,
                    enabled = state.actionsEnabled,
                    isSending = state.isSendingReply,
                    templates = state.templates,
                    approvesParent = comment.status == CommentStatus.PENDING,
                    onSend = onSendReply,
                )
            }
        }
    }
}

@Composable
private fun CommentHeader(comment: Comment, showEmail: Boolean, showAvatar: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 16.dp),
    ) {
        // Ohne diese Bedingung entstünde eine Anfrage an Gravatar, obwohl die
        // Einstellung dagegen steht - die Liste beachtet sie, die Detailansicht
        // tat es bisher nicht.
        if (showAvatar) {
            CommentAvatar(url = comment.avatarUrl, size = 48.dp)
            Spacer(Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = comment.authorName.ifBlank {
                    stringResource(R.string.comment_author_unknown)
                },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = RelativeTime.absolute(comment.date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        StatusChip(status = comment.status)
    }

    val stil = MaterialTheme.typography.bodySmall
    // Alle drei Beschriftungen fliessen in die Breite ein, auch wenn gerade
    // nur eine davon angezeigt wird: Sonst ruckte die Spalte, sobald eine
    // Zeile dazukommt.
    val beschriftungen = listOf(
        stringResource(R.string.detail_post),
        stringResource(R.string.detail_website),
        stringResource(R.string.detail_email),
    )
    val spaltenbreite = rememberLabelWidth(beschriftungen, stil)

    Column(modifier = Modifier.padding(top = 12.dp)) {
        comment.postTitle?.let {
            DetailRow(beschriftungen[0], it, spaltenbreite, stil)
        }
        comment.authorUrl?.let {
            DetailRow(beschriftungen[1], it, spaltenbreite, stil)
        }
        // Die E-Mail-Adresse ist ein personenbezogenes Datum und wird nur
        // angezeigt, wenn sie ausdrücklich eingeschaltet wurde.
        if (showEmail) {
            comment.authorEmail?.let {
                DetailRow(beschriftungen[2], it, spaltenbreite, stil)
            }
        }
    }
}

/**
 * Eine Zeile aus Beschriftung und Wert.
 *
 * Die Spaltenbreite kommt von aussen und ist gemessen, nicht gesetzt: Eine
 * feste Breite brach bei grosser Schrift mitten im Wort um.
 */
@Composable
private fun DetailRow(
    label: String,
    value: String,
    labelWidth: Dp,
    style: TextStyle,
) {
    Row(
        // Zusammengefasst, damit ein Bildschirmleser "Beitrag Hello world!"
        // in einem Zug liest. Getrennt waeren es zwei Stationen, von denen
        // die erste fuer sich genommen nichts aussagt.
        modifier = Modifier
            .padding(vertical = 2.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = label,
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(labelWidth),
        )
        Spacer(Modifier.width(12.dp))
        Text(text = value, style = style)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModerationActions(
    status: CommentStatus,
    enabled: Boolean,
    onModerate: (ModerationAction) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (status != CommentStatus.APPROVED) {
            ActionButton(
                icon = Icons.Default.CheckCircle,
                label = stringResource(R.string.action_approve),
                enabled = enabled,
                onClick = { onModerate(ModerationAction.Approve) },
            )
        }
        if (status != CommentStatus.PENDING) {
            ActionButton(
                icon = Icons.Default.Info,
                label = stringResource(R.string.action_hold),
                enabled = enabled,
                onClick = { onModerate(ModerationAction.Hold) },
            )
        }
        if (status != CommentStatus.SPAM) {
            ActionButton(
                icon = Icons.Default.Warning,
                label = stringResource(R.string.action_spam),
                enabled = enabled,
                onClick = { onModerate(ModerationAction.MarkAsSpam) },
            )
        }
        if (status != CommentStatus.TRASH) {
            ActionButton(
                icon = Icons.Default.Delete,
                label = stringResource(R.string.action_trash),
                enabled = enabled,
                onClick = { onModerate(ModerationAction.Delete(permanent = false)) },
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, enabled = enabled) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

/**
 * Eine Antwort im Faden.
 *
 * Eingerückt und mit einer senkrechten Linie am linken Rand: Ohne die sähen
 * Antworten wie eigenständige Kommentare aus, und der Bezug zum darüber
 * stehenden Beitrag ginge verloren. Die Linie ist über `IntrinsicSize.Min` so
 * hoch wie die Karte daneben.
 */
@Composable
private fun ReplyItem(reply: Comment, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp)
            .height(IntrinsicSize.Min),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
        )

        Spacer(Modifier.width(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = reply.authorName,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    StatusChip(status = reply.status)
                }
                Text(
                    text = reply.contentPlain,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = RelativeTime.relative(reply.date, LocalResources.current).toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Haengt einen Baustein an den vorhandenen Text an, statt ihn zu ersetzen.
 *
 * Ersetzen waere der haeufigere Fall, aber der teurere Irrtum: Ein aus
 * Versehen geloeschter, halb getippter Absatz laesst sich nicht
 * wiederherstellen, ein zu viel eingefuegter Satz dagegen schon.
 */
internal fun insertTemplate(current: String, template: String): String =
    if (current.isBlank()) template else current.trimEnd() + "\n\n" + template

@Composable
private fun TemplateStrip(
    templates: List<ReplyTemplate>,
    onPick: (ReplyTemplate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.templates_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            items(templates, key = { it.id }) { template ->
                AssistChip(
                    onClick = { onPick(template) },
                    label = { Text(template.shortLabel) },
                )
            }
        }
    }
}

@Composable
private fun ReplyComposer(
    text: String,
    templates: List<ReplyTemplate>,
    onTextChange: (String) -> Unit,
    enabled: Boolean,
    isSending: Boolean,
    onSend: () -> Unit,
    /** Ob das Senden den Kommentar zugleich freigibt - das soll der Knopf sagen. */
    approvesParent: Boolean = false,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        if (templates.isNotEmpty() && enabled && !isSending) {
            TemplateStrip(
                templates = templates,
                onPick = { template -> onTextChange(insertTemplate(text, template.text)) },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            enabled = enabled && !isSending,
            label = { Text(stringResource(R.string.reply_label)) },
            placeholder = { Text(stringResource(R.string.reply_placeholder)) },
            supportingText = if (!enabled) {
                { Text(stringResource(R.string.reply_disabled_hint)) }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )

        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Button(
                onClick = onSend,
                enabled = enabled && !isSending && text.isNotBlank(),
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (approvesParent) R.string.action_approve_and_reply else R.string.action_send_reply,
                    ),
                )
            }
        }
    }
}

@Composable
private fun OverflowMenu(
    enabled: Boolean,
    canBlockAuthor: Boolean,
    onEdit: () -> Unit,
    onBlockAuthor: () -> Unit,
    onDeletePermanently: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = stringResource(R.string.action_more),
        )
    }

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_edit)) },
            enabled = enabled,
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
            onClick = {
                expanded = false
                onEdit()
            },
        )
        if (canBlockAuthor) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_block_author)) },
                enabled = enabled,
                leadingIcon = { Icon(Icons.Default.Clear, contentDescription = null) },
                onClick = {
                    expanded = false
                    onBlockAuthor()
                },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete_permanently)) },
            enabled = enabled,
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
            onClick = {
                expanded = false
                onDeletePermanently()
            },
        )
    }
}

@Composable
private fun ConfirmPermanentDeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_delete_title)) },
        text = { Text(stringResource(R.string.dialog_delete_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete_permanently))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun EditCommentDialog(
    initialText: String,
    isSaving: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable(initialText) { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_edit_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                enabled = !isSaving,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = !isSaving && text.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun ModerationAction.messageRes(): Int = when (this) {
    ModerationAction.Approve -> R.string.snackbar_approved
    ModerationAction.Hold -> R.string.snackbar_held
    ModerationAction.MarkAsSpam -> R.string.snackbar_spam
    is ModerationAction.Delete ->
        if (permanent) R.string.snackbar_deleted else R.string.snackbar_trashed
}
