package de.christophlangner.commentator.ui.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.christophlangner.commentator.R
import de.christophlangner.commentator.domain.model.NotifyScope
import de.christophlangner.commentator.domain.model.Team
import de.christophlangner.commentator.domain.model.TeamRole
import de.christophlangner.commentator.domain.repository.ReplyTemplate
import de.christophlangner.commentator.ui.common.BlogTitle
import de.christophlangner.commentator.ui.common.ErrorTexts

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

    LifecycleResumeEffect(Unit) {
        viewModel.refreshPushAvailability()
        onPauseOrDispose { }
    }
    val activity = LocalActivity.current

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
            onPushTest = viewModel::sendPushTest,
            onInstantPush = { an ->
                when {
                    !an -> viewModel.disableInstantPush()
                    activity != null -> viewModel.chooseDistributor(activity)
                }
            },
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
    onInstantPush: (Boolean) -> Unit = {},
    onPushTest: () -> Unit = {},
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
                SettingsDescription(
                    text = stringResource(R.string.settings_no_moderation_rights),
                    color = MaterialTheme.colorScheme.error,
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

        SettingsLabel(stringResource(R.string.settings_scope))
        // Eine Liste statt Chips: Drei längere Begriffe brachen als Chips
        // unschön um, und was sie unterscheidet, stand in einem Absatz
        // darunter. So steht die Erklärung bei der Option.
        NotifyScope.entries.forEach { scope ->
            RadioRow(
                title = stringResource(scope.labelRes()),
                description = stringResource(scope.descriptionRes()),
                selected = state.settings.notifyScope == scope,
                onClick = { onNotifyScope(scope) },
                enabled = state.settings.notificationsEnabled,
            )
        }

        InstantPushRow(state = state, onInstantPush = onInstantPush)
        if (state.settings.instantPush && state.settings.pushEndpoint != null) {
            PushTestRow(test = state.pushTest, onPushTest = onPushTest)
        }

        HorizontalDivider()
        SectionTitle(stringResource(R.string.settings_team))

        SettingsDescription(stringResource(R.string.settings_team_description))

        if (state.availableRoles.isEmpty()) {
            SettingsDescription(stringResource(R.string.settings_team_needs_plugin))
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

        SettingsDescription(stringResource(R.string.templates_description))

        if (state.templates.isEmpty()) {
            Text(
                text = stringResource(R.string.templates_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = SettingsSpacing.Edge, vertical = SettingsSpacing.Row),
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

        SettingsTextButton(
            text = stringResource(R.string.templates_add),
            onClick = onAddTemplate,
            enabled = state.canAddTemplate,
        )
        if (!state.canAddTemplate) {
            SettingsDescription(stringResource(R.string.templates_full, ReplyTemplate.MAX_TEMPLATES))
        }

        HorizontalDivider(modifier = Modifier.padding(top = SettingsSpacing.Gap))
        SettingsTextButton(
            text = stringResource(R.string.action_remove_site),
            onClick = onSignOutRequest,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(SettingsSpacing.Gap))
    }
}

/**
 * Sofortmeldung über UnifiedPush.
 *
 * Abschalten geht immer, Einschalten nur, wenn alles da ist: das Plugin auf
 * dem Blog und eine UnifiedPush-App auf dem Telefon. Fehlt etwas, sagt die
 * Beschreibung, was - statt eines Schalters, der ohne Erklärung grau bleibt.
 */
@Composable
private fun InstantPushRow(state: SiteSettingsUiState, onInstantPush: (Boolean) -> Unit) {
    val an = state.settings.instantPush
    val plugin = state.instance?.hasBridgePlugin == true
    val resources = LocalResources.current

    val beschreibung = when {
        state.pushError != null -> stringResource(
            R.string.settings_push_error,
            ErrorTexts.message(resources, state.pushError),
        )
        an && state.settings.pushEndpoint != null -> {
            val server = pushServer(state.settings.pushEndpoint)
            val aktiv = stringResource(
                R.string.settings_push_active,
                state.pushDistributor ?: stringResource(R.string.settings_push_distributor_fallback),
                server,
            )
            // Der öffentliche Server funktioniert, gehört aber einem Anbieter.
            // Wer einen eigenen betreibt, soll wissen, wo er umstellt.
            if (server == PUBLIC_NTFY) {
                aktiv + " " + stringResource(R.string.settings_push_public_server)
            } else {
                aktiv
            }
        }
        an -> stringResource(R.string.settings_push_pending)
        !plugin -> stringResource(R.string.settings_push_needs_plugin)
        !state.pushAvailable -> stringResource(R.string.settings_push_needs_distributor)
        else -> stringResource(R.string.settings_push_description)
    }

    SwitchRow(
        title = stringResource(R.string.settings_push),
        description = beschreibung,
        checked = an,
        onCheckedChange = onInstantPush,
        enabled = an || (state.settings.notificationsEnabled && plugin && state.pushAvailable),
    )
}

private const val PUBLIC_NTFY = "ntfy.sh"

/** Der Server hinter einer Push-Adresse, so wie man ihn in ntfy einträgt. */
internal fun pushServer(endpoint: String): String =
    runCatching { java.net.URI(endpoint).host }.getOrNull() ?: endpoint

/**
 * Selbsttest der Sofortmeldung: Das Plugin schickt einen Weckruf, und kommt
 * er an, bestätigt das eine Benachrichtigung. Beantwortet die Frage, die sonst
 * nur Warten beantwortet - ob der Weg über Blog, Push-Server und
 * UnifiedPush-App wirklich trägt.
 */
@Composable
private fun PushTestRow(test: PushTest?, onPushTest: () -> Unit) {
    val resources = LocalResources.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // Rechts nur so viel, dass der Text des Knopfes mit dem Schalter
            // darüber abschließt; der Knopf bringt eigenen Innenabstand mit.
            .padding(start = SettingsSpacing.Edge, end = SettingsSpacing.TextButtonEdge, bottom = SettingsSpacing.Gap),
    ) {
        Text(
            text = when (test) {
                null -> stringResource(R.string.settings_push_test_hint)
                PushTest.Sending -> stringResource(R.string.settings_push_test_sending)
                is PushTest.Sent -> if (test.accepted > 0) {
                    stringResource(R.string.settings_push_test_sent)
                } else {
                    stringResource(R.string.settings_push_test_none)
                }
                is PushTest.Failed -> stringResource(
                    R.string.settings_push_test_failed,
                    ErrorTexts.message(resources, test.error),
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onPushTest, enabled = test != PushTest.Sending) {
            Text(stringResource(R.string.settings_push_test))
        }
    }
}

