package de.christophlangner.commentator.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.christophlangner.commentator.R
import de.christophlangner.commentator.domain.model.CommentSignals

/**
 * Autorenkontext und Auffälligkeiten als ruhige Zeile kleiner Marken.
 *
 * Bewusst zurückhaltend gestaltet, ohne Warnfarbe: Das sind Hinweise, keine
 * Urteile. Eine rote Markierung würde eine Bewertung nahelegen, die die App
 * nicht vornimmt – die Entscheidung bleibt beim Menschen.
 *
 * Gibt es nichts zu melden, entsteht auch kein Abstand.
 */
@Composable
fun SignalChips(
    signals: CommentSignals,
    modifier: Modifier = Modifier,
    approvedByAuthor: Int? = null,
) {
    val resources = LocalResources.current
    val labels = buildList {
        when {
            approvedByAuthor == null -> Unit
            approvedByAuthor == 0 -> add(stringResource(R.string.signal_first_time_author))
            else -> add(
                resources.getQuantityString(
                    R.plurals.signal_known_author,
                    approvedByAuthor,
                    approvedByAuthor,
                ),
            )
        }
        if (signals.linkCount > 0) {
            add(
                resources.getQuantityString(
                    R.plurals.signal_links,
                    signals.linkCount,
                    signals.linkCount,
                ),
            )
        }
        if (signals.duplicated) add(stringResource(R.string.signal_duplicate))
    }
    if (labels.isEmpty()) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        labels.forEach { label ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}
