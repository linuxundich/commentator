package de.christophlangner.commentator.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.christophlangner.commentator.R
import de.christophlangner.commentator.domain.model.AppIcon
import de.christophlangner.commentator.ui.common.BlogTitle
import de.christophlangner.commentator.domain.repository.ReplyTemplate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    onSignedOut: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showSignOutDialog by rememberSaveable { mutableStateOf(false) }
    // null = kein Dialog offen, "" = neuer Baustein, sonst die Kennung des
    // zu bearbeitenden.
    var editingTemplateId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state.isSignedOut) {
        if (state.isSignedOut) onSignedOut()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        SettingsContent(
            state = state,
            intervalOptions = viewModel.intervalOptions,
            onNotificationsEnabled = viewModel::setNotificationsEnabled,
            onSyncInterval = viewModel::setSyncInterval,
            onAppIcon = viewModel::setAppIcon,
            onShowAvatars = viewModel::setShowAvatars,
            onShowAuthorEmail = viewModel::setShowAuthorEmail,
            onOpenSystemNotifications = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            },
            onSignOutRequest = { showSignOutDialog = true },
            onOpenAbout = onOpenAbout,
            onAddTemplate = { editingTemplateId = "" },
            onEditTemplate = { editingTemplateId = it.id },
            onDeleteTemplate = viewModel::removeTemplate,
            modifier = Modifier.padding(innerPadding),
        )
    }

    editingTemplateId?.let { id ->
        val existing = state.templates.firstOrNull { it.id == id }
        TemplateDialog(
            initialText = existing?.text.orEmpty(),
            onDismiss = { editingTemplateId = null },
            onConfirm = { text ->
                if (existing == null) {
                    viewModel.addTemplate(text)
                } else {
                    viewModel.updateTemplate(existing.id, text)
                }
                editingTemplateId = null
            },
        )
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text(stringResource(R.string.dialog_sign_out_title)) },
            text = { Text(stringResource(R.string.dialog_sign_out_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutDialog = false
                    viewModel.signOut()
                }) {
                    Text(stringResource(R.string.action_sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * Der Inhalt ohne ViewModel und ohne Zugriff auf das System, damit er sich
 * ohne Hilt prüfen lässt - wie bei den übrigen Bildschirmen.
 */
@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    intervalOptions: List<Int>,
    onNotificationsEnabled: (Boolean) -> Unit,
    onSyncInterval: (Int) -> Unit,
    onAppIcon: (AppIcon) -> Unit,
    onShowAvatars: (Boolean) -> Unit,
    onShowAuthorEmail: (Boolean) -> Unit,
    onOpenSystemNotifications: () -> Unit,
    onSignOutRequest: () -> Unit,
    onOpenAbout: () -> Unit,
    onAddTemplate: () -> Unit,
    onEditTemplate: (ReplyTemplate) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SectionTitle(stringResource(R.string.settings_section_account))
        state.instance?.let { instance ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_blog),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(96.dp),
                )
                BlogTitle(
                    name = instance.displayName,
                    iconUrl = instance.iconUrl,
                    iconSize = 24.dp,
                )
            }
            InfoRow(stringResource(R.string.settings_url), instance.siteUrl)
            InfoRow(stringResource(R.string.settings_user), instance.username)
            InfoRow(
                stringResource(R.string.settings_plugin),
                stringResource(
                    if (instance.hasBridgePlugin) {
                        R.string.settings_plugin_detected
                    } else {
                        R.string.settings_plugin_missing
                    },
                ),
            )
            if (!instance.canModerate) {
                Text(
                    text = stringResource(R.string.settings_no_moderation_rights),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        HorizontalDivider()
        SectionTitle(stringResource(R.string.settings_section_notifications))

        SwitchRow(
            title = stringResource(R.string.settings_notifications),
            description = stringResource(R.string.settings_notifications_description),
            checked = state.settings.notificationsEnabled,
            onCheckedChange = onNotificationsEnabled,
        )

        Text(
            text = stringResource(R.string.settings_interval),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        )
        // FlowRow statt Row: Die Auswahl hat mehr Einträge, als nebeneinander
        // passen. In einer Row bliebe für den letzten Chip fast keine
        // Breite übrig, sein Text bräche senkrecht um und zöge die ganze
        // Zeile in die Länge.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            intervalOptions.forEach { minutes ->
                FilterChip(
                    selected = state.settings.syncIntervalMinutes == minutes,
                    onClick = { onSyncInterval(minutes) },
                    enabled = state.settings.notificationsEnabled,
                    label = { Text(stringResource(R.string.settings_minutes, minutes)) },
                )
            }
        }

        TextButton(
            onClick = onOpenSystemNotifications,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text(stringResource(R.string.settings_open_system_notifications))
        }

        HorizontalDivider()
        SectionTitle(stringResource(R.string.settings_section_appearance))

        Text(
            text = stringResource(R.string.settings_app_icon),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            AppIcon.entries.forEach { icon ->
                FilterChip(
                    selected = state.appIcon == icon,
                    onClick = { onAppIcon(icon) },
                    label = { Text(stringResource(icon.labelRes())) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(icon.swatch()),
                        )
                    },
                )
            }
        }
        Text(
            text = stringResource(R.string.settings_app_icon_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )

        HorizontalDivider()
        SectionTitle(stringResource(R.string.templates_section))

        Text(
            text = stringResource(R.string.templates_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        if (state.templates.isEmpty()) {
            Text(
                text = stringResource(R.string.templates_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        } else {
            state.templates.forEach { template ->
                TemplateRow(
                    template = template,
                    onEdit = { onEditTemplate(template) },
                    onDelete = { onDeleteTemplate(template.id) },
                )
            }
        }

        TextButton(
            onClick = onAddTemplate,
            enabled = state.canAddTemplate,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text(stringResource(R.string.templates_add))
        }
        if (!state.canAddTemplate) {
            Text(
                text = stringResource(R.string.templates_full, ReplyTemplate.MAX_TEMPLATES),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        HorizontalDivider()
        SectionTitle(stringResource(R.string.settings_section_privacy))

        SwitchRow(
            title = stringResource(R.string.settings_avatars),
            description = stringResource(R.string.settings_avatars_description),
            checked = state.settings.showAvatars,
            onCheckedChange = onShowAvatars,
        )
        SwitchRow(
            title = stringResource(R.string.settings_author_email),
            description = stringResource(R.string.settings_author_email_description),
            checked = state.settings.showAuthorEmail,
            onCheckedChange = onShowAuthorEmail,
        )

        HorizontalDivider()
        TextButton(
            onClick = onOpenAbout,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text(stringResource(R.string.about_open))
        }

        TextButton(
            onClick = onSignOutRequest,
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.action_sign_out),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun TemplateRow(
    template: ReplyTemplate,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Text(
            text = template.text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.templates_delete),
            )
        }
    }
}

/**
 * Dialog zum Anlegen und Bearbeiten.
 *
 * Derselbe Dialog fuer beides: Der Unterschied ist allein, ob ein Text
 * vorbelegt ist.
 */
@Composable
private fun TemplateDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initialText.isEmpty()) R.string.templates_add else R.string.templates_edit,
                ),
            )
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.templates_text_label)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun AppIcon.labelRes(): Int = when (this) {
    AppIcon.Green -> R.string.settings_app_icon_green
    AppIcon.Blue -> R.string.settings_app_icon_blue
}

/** Farbtupfer neben der Bezeichnung - die Farbe ist hier die eigentliche Aussage. */
private fun AppIcon.swatch(): Color = when (this) {
    AppIcon.Green -> Color(0xFF3DDC84)
    AppIcon.Blue -> Color(0xFF3858E9)
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // Die ganze Zeile schaltet, damit das Berührungsziel groß genug ist.
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
