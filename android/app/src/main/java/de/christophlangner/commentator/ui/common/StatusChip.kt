package de.christophlangner.commentator.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.christophlangner.commentator.R
import de.christophlangner.commentator.domain.model.CommentStatus

/**
 * Statuskennzeichnung eines Kommentars.
 *
 * Farbe allein wäre nicht barrierefrei, deshalb tragen die Marken immer
 * zusätzlich ein Symbol und einen Text.
 */
@Composable
fun StatusChip(
    status: CommentStatus,
    modifier: Modifier = Modifier,
) {
    val colors = status.chipColors()
    val label = stringResource(status.labelRes())

    Surface(
        modifier = modifier,
        color = colors.first,
        contentColor = colors.second,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = status.icon(),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                // Eine Marke ist einzeilig. Ohne das bricht sie unter Druck
                // mitten im Wort um - "Genehmig" ueber "t" - statt der Zeile
                // daneben den Platz zu nehmen.
                maxLines = 1,
                // Kein clearAndSetSemantics: Der Status muss vorgelesen werden.
                // Das Symbol daneben ist als dekorativ ausgezeichnet, sodass
                // nichts doppelt angesagt wird.
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun CommentStatus.chipColors(): Pair<Color, Color> = when (this) {
    CommentStatus.APPROVED ->
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer

    CommentStatus.PENDING ->
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer

    CommentStatus.SPAM ->
        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer

    CommentStatus.TRASH ->
        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
}

// Bewusst nur Symbole aus material-icons-core: Das Paket
// material-icons-extended ist eingefroren und würde tausende ungenutzte
// Vektoren mitbringen.
private fun CommentStatus.icon(): ImageVector = when (this) {
    CommentStatus.APPROVED -> Icons.Default.CheckCircle
    CommentStatus.PENDING -> Icons.Default.Info
    CommentStatus.SPAM -> Icons.Default.Warning
    CommentStatus.TRASH -> Icons.Default.Delete
}

fun CommentStatus.labelRes(): Int = when (this) {
    CommentStatus.APPROVED -> R.string.status_approved
    CommentStatus.PENDING -> R.string.status_pending
    CommentStatus.SPAM -> R.string.status_spam
    CommentStatus.TRASH -> R.string.status_trash
}
