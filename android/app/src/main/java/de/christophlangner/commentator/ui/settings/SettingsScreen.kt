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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import de.christophlangner.commentator.R
import de.christophlangner.commentator.domain.model.AppIcon
import de.christophlangner.commentator.domain.model.WordPressInstance

/**
 * Einstellungen, die für alle Blogs gelten.
 *
 * Was an einem einzelnen Blog hängt - seine Rollen, seine Vorlagen, ob er
 * meldet - steht im [SiteSettingsScreen] dahinter. Zusammen auf einem
 * Bildschirm wäre bei jeder Zeile zu klären, für welchen Blog sie gilt; mit
 * drei Blogs wäre das eine Liste, in der man sucht statt einstellt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSite: (String) -> Unit,
    onAddSite: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
            onThreadedInbox = viewModel::setThreadedInbox,
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
            onOpenSite = onOpenSite,
            onAddSite = onAddSite,
            onOpenAbout = onOpenAbout,
            modifier = Modifier.padding(innerPadding),
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
    onThreadedInbox: (Boolean) -> Unit,
    onSyncInterval: (Int) -> Unit,
    onAppIcon: (AppIcon) -> Unit,
    onShowAvatars: (Boolean) -> Unit,
    onShowAuthorEmail: (Boolean) -> Unit,
    onOpenSystemNotifications: () -> Unit,
    onOpenSite: (String) -> Unit,
    onAddSite: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SectionTitle(stringResource(R.string.settings_section_sites))

        state.instances.forEach { instance ->
            SiteEntry(
                instance = instance,
                isActive = instance.id == state.activeInstanceId,
                onClick = { onOpenSite(instance.id) },
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onAddSite)
                .padding(horizontal = SettingsSpacing.Edge, vertical = SettingsSpacing.Row),
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.sites_add),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        HorizontalDivider()
        SectionTitle(stringResource(R.string.settings_section_notifications))

        SwitchRow(
            title = stringResource(R.string.settings_notifications),
            description = stringResource(R.string.settings_notifications_description),
            checked = state.settings.notificationsEnabled,
            onCheckedChange = onNotificationsEnabled,
        )

        // Dass der Umfang je Blog eingestellt wird, muss hier stehen: Sonst
        // sucht man ihn unter dem Hauptschalter, wo er früher war.
        SettingsDescription(stringResource(R.string.settings_notifications_per_site_hint))

        SettingsLabel(stringResource(R.string.settings_interval))
        ChipGroup {
            intervalOptions.forEach { minutes ->
                FilterChip(
                    selected = state.settings.syncIntervalMinutes == minutes,
                    onClick = { onSyncInterval(minutes) },
                    enabled = state.settings.notificationsEnabled,
                    label = { Text(stringResource(R.string.settings_minutes, minutes)) },
                )
            }
        }
        // Der Takt gilt für den Durchgang über alle Blogs, nicht je Blog.
        SettingsDescription(stringResource(R.string.settings_interval_description))

        SettingsTextButton(
            text = stringResource(R.string.settings_open_system_notifications),
            onClick = onOpenSystemNotifications,
        )

        HorizontalDivider()
        SectionTitle(stringResource(R.string.settings_section_appearance))

        SettingsLabel(stringResource(R.string.settings_app_icon))
        ChipGroup {
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
        SettingsDescription(stringResource(R.string.settings_app_icon_hint))

        SwitchRow(
            title = stringResource(R.string.settings_threaded),
            description = stringResource(R.string.settings_threaded_description),
            checked = state.settings.threadedInbox,
            onCheckedChange = onThreadedInbox,
        )

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

        HorizontalDivider(modifier = Modifier.padding(top = SettingsSpacing.Gap))
        SettingsTextButton(text = stringResource(R.string.about_open), onClick = onOpenAbout)
        Spacer(Modifier.height(SettingsSpacing.Gap))
    }
}

/**
 * Ein Blog in der Liste.
 *
 * Name, Adresse und - beim angezeigten Blog - ein Hinweis darauf. Die Adresse
 * gehört dazu: Zwei Blogs können denselben Namen tragen.
 */
@Composable
private fun SiteEntry(
    instance: WordPressInstance,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = SettingsSpacing.Edge, vertical = SettingsSpacing.Row),
    ) {
        if (instance.displayIconUrl != null) {
            AsyncImage(
                model = instance.displayIconUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
        } else {
            Spacer(Modifier.size(28.dp))
        }
        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = instance.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = instance.siteUrl.removePrefix("https://"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Nur beim angezeigten Blog, und nur wenn ihm etwas fehlt: Der
            // Hinweis auf fehlende Moderationsrechte gehört zu jedem Blog und
            // steht deshalb auch in seinen eigenen Einstellungen.
            if (!instance.canModerate) {
                Text(
                    text = stringResource(R.string.settings_no_moderation_rights_short),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        if (isActive) {
            Text(
                text = stringResource(R.string.sites_active),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
