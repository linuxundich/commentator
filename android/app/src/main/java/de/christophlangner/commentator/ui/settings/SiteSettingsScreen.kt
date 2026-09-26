package de.christophlangner.commentator.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.christophlangner.commentator.R
import de.christophlangner.commentator.domain.model.NotifyScope
import de.christophlangner.commentator.domain.model.Team
import de.christophlangner.commentator.domain.model.TeamRole
import de.christophlangner.commentator.domain.repository.ReplyTemplate
import de.christophlangner.commentator.ui.common.BlogTitle

/**
 * Die Einstellungen eines einzelnen Blogs.
 *
 * Alles hier gilt nur für diesen Blog: ob er meldet, worüber, wer dort zum
 * Team gehört und mit welchen Vorlagen dort geantwortet wird. Der Name in der
 * Kopfleiste sagt durchgehend, um welchen es geht - bei mehreren Blogs ist
 * das die wichtigste Angabe des Bildschirms.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteSettingsScreen(
    onNavigateUp: () -> Unit,
    onLastSiteRemoved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SiteSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var showSignOutDialog by rememberSaveable { mutableStateOf(false) }
    // null = kein Dialog offen, "" = neuer Baustein, sonst die Kennung des
    // zu bearbeitenden.
    var editingTemplateId by rememberSaveable { mutableStateOf<String?>(null) }
    // Das Kuerzel der Rolle, deren Dialog offen ist; null = keiner.
    var editingRole by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state.removed) {
        if (!state.removed) return@LaunchedEffect
        if (state.wasLast) onLastSiteRemoved() else onNavigateUp()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    // Der Blogname statt "Einstellungen": Welcher Blog gemeint
                    // ist, ist hier die Frage, die sich sonst bei jeder Zeile
                    // neu stellt.
                    BlogTitle(
                        name = state.instance?.displayName
                            ?: stringResource(R.string.settings_title),
                        iconUrl = state.instance?.displayIconUrl,
                        iconSize = 24.dp,
                    )
                },
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
        SiteSettingsContent(
            state = state,
            onNotificationsEnabled = viewModel::setNotificationsEnabled,
            onNotifyScope = viewModel::setNotifyScope,
            onOpenRole = { editingRole = it.slug },
            onAddTemplate = { editingTemplateId = "" },
            onEditTemplate = { editingTemplateId = it.id },
            onDeleteTemplate = viewModel::removeTemplate,
            onSignOutRequest = { showSignOutDialog = true },
            modifier = Modifier.padding(innerPadding),
        )
    }

    // Meldet der Blog seine Rollen nachträglich anders, kann die Rolle
    // verschwunden sein; dann schliesst sich der Dialog einfach.
    editingRole?.let { slug ->
        konfigurierbareRollen(state.availableRoles).firstOrNull { it.slug == slug }
    }?.let { rolle ->
        val slug = rolle.slug
        RoleDialog(
            role = rolle,
            isTeam = slug == Team.SELF || slug in state.settings.teamRoles,
            canLeaveTeam = slug != Team.SELF,
            style = state.settings.roleStyles.of(slug),
            onToggleTeam = { viewModel.toggleTeamRole(slug) },
            onStyleChange = { viewModel.setRoleStyle(slug, it) },
            onDismiss = { editingRole = null },
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
            text = {
                // Der Name im Text, nicht nur im Titel: Bei mehreren Blogs ist
                // "Abmelden" allein keine Frage, die man beantworten kann.
                Text(
                    stringResource(
                        R.string.dialog_sign_out_message_site,
                        state.instance?.displayName.orEmpty(),
                    ),
                )
            },
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
 * Der Inhalt ohne ViewModel, damit er sich ohne Hilt prüfen lässt - wie bei
 * den übrigen Bildschirmen.
 */
@Composable
internal fun SiteSettingsContent(
    state: SiteSettingsUiState,
    onNotificationsEnabled: (Boolean) -> Unit,
    onNotifyScope: (NotifyScope) -> Unit,
    onOpenRole: (TeamRole) -> Unit,
    onAddTemplate: () -> Unit,
    onEditTemplate: (ReplyTemplate) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onSignOutRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SectionTitle(stringResource(R.string.settings_section_account))

        state.instance?.let { instance ->
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
            title = stringResource(R.string.settings_site_notifications),
            description = stringResource(R.string.settings_site_notifications_description),
            checked = state.settings.notificationsEnabled,
            onCheckedChange = onNotificationsEnabled,
        )

        Text(
            text = stringResource(R.string.settings_scope),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            NotifyScope.entries.forEach { scope ->
                FilterChip(
                    selected = state.settings.notifyScope == scope,
                    onClick = { onNotifyScope(scope) },
                    enabled = state.settings.notificationsEnabled,
                    label = { Text(stringResource(scope.labelRes())) },
                )
            }
        }
        Text(
            text = stringResource(R.string.settings_scope_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        HorizontalDivider()
        SectionTitle(stringResource(R.string.settings_team))

        Text(
            text = stringResource(R.string.settings_team_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        if (state.availableRoles.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_team_needs_plugin),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        // Je Rolle eine Zeile statt eines Feldes von Schaltern: Zu jeder Rolle
        // gehoeren vier Entscheidungen, und die alle nebeneinander zu legen
        // ergaebe eine Wand aus Schaltern, in der niemand mehr sieht, was
        // wozu gehoert.
        konfigurierbareRollen(state.availableRoles).forEach { role ->
            RoleRow(
                role = role,
                isTeam = role.slug == Team.SELF || role.slug in state.settings.teamRoles,
                style = state.settings.roleStyles.of(role.slug),
                onClick = { onOpenRole(role) },
            )
        }

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
        TextButton(
            onClick = onSignOutRequest,
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.action_remove_site),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
