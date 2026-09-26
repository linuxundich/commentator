package de.christophlangner.commentator.ui.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import de.christophlangner.commentator.R
import de.christophlangner.commentator.domain.model.WordPressInstance
import de.christophlangner.commentator.ui.common.BlogTitle

/**
 * Der Blogname in der Kopfleiste, bei mehreren Blogs als Umschalter.
 *
 * Steht nur ein Blog, bleibt es beim reinen Titel: Ein Pfeil, hinter dem eine
 * Liste mit einem Eintrag liegt, wäre ein Versprechen ohne Inhalt. Hinzufügen
 * geht dann über die Einstellungen.
 */
@Composable
fun BlogTitleSwitcher(
    instance: WordPressInstance?,
    instanceCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = instance?.displayName ?: stringResource(R.string.app_name)
    val switchable = instanceCount > 1
    // Die Beschriftung der Aktion und nicht eine eigene für den Pfeil: So
    // liest die Sprachausgabe „Blogname, Doppeltippen zum Blog wechseln" statt
    // Name und Pfeil als zwei Dinge nebeneinander.
    val label = stringResource(R.string.sites_switch)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (switchable) {
            modifier.clickable(onClickLabel = label, onClick = onClick)
        } else {
            modifier
        },
    ) {
        BlogTitle(
            name = name,
            iconUrl = instance?.displayIconUrl,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (switchable) {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                // Der Pfeil zeigt nur, dass es weitergeht; benannt ist die
                // Aktion an der Zeile.
                contentDescription = null,
            )
        }
    }
}

/**
 * Die Liste der eingerichteten Blogs.
 *
 * Als Blatt von unten und nicht als Menü: Auf der Liste stehen Symbol, Name
 * und Adresse jedes Blogs, und die Adresse ist nötig - zwei Blogs können
 * denselben Namen tragen, die Adresse unterscheidet sie immer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteSwitcherSheet(
    instances: List<WordPressInstance>,
    activeId: String?,
    pendingPerInstance: Map<String, Int>,
    onSelect: (String) -> Unit,
    onAddSite: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.sites_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            instances.forEach { instance ->
                SiteRow(
                    instance = instance,
                    isActive = instance.id == activeId,
                    pending = pendingPerInstance[instance.id] ?: 0,
                    onClick = { onSelect(instance.id) },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAddSite)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.sites_add),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun SiteRow(
    instance: WordPressInstance,
    isActive: Boolean,
    pending: Int,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        // Kein Platzhalter, wenn der Blog kein Symbol hat: Die Zeile bleibt
        // trotzdem ausgerichtet, weil die Fläche reserviert ist.
        if (instance.displayIconUrl != null) {
            AsyncImage(
                model = instance.displayIconUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
        } else {
            Spacer(Modifier.size(32.dp))
        }
        Spacer(Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = instance.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // Ohne Schema: Das `https://` ist auf jeder Zeile dasselbe und
                // kostet nur Platz, den der Name braucht.
                text = instance.siteUrl.removePrefix("https://"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (pending > 0) {
                Text(
                    text = pluralStringResource(R.plurals.sites_pending, pending, pending),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (isActive) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.sites_active),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
