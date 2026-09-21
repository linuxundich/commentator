package de.christophlangner.commentator.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.christophlangner.commentator.R
import de.christophlangner.commentator.domain.model.Comment
import de.christophlangner.commentator.domain.model.CommentSignals
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.model.ModerationAction
import de.christophlangner.commentator.domain.model.TeamRole

/**
 * Kommentarkarte mit Wischgesten.
 *
 * Nach rechts genehmigen, nach links als Spam markieren – die beiden
 * Entscheidungen, die im Alltag fast alle Fälle abdecken. Die Schaltflächen
 * auf der Karte bleiben: Eine Geste darf nie der einzige Weg zu einer Aktion
 * sein, weder für Bildschirmleser noch für Menschen mit eingeschränkter
 * Feinmotorik.
 *
 * Beide Richtungen sind rücknehmbar; die Meldung nach der Aktion bietet das
 * Zurücknehmen an. Löschen ist bewusst **nicht** dabei: Eine Geste, die
 * versehentlich ausgelöst werden kann, darf nichts Endgültiges tun.
 */
@Composable
fun SwipeableCommentCard(
    comment: Comment,
    signals: CommentSignals,
    teamRole: TeamRole?,
    showAvatar: Boolean,
    actionsEnabled: Boolean,
    onOpen: () -> Unit,
    onModerate: (ModerationAction) -> Unit,
    onReply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kannGenehmigen = actionsEnabled && comment.status != CommentStatus.APPROVED
    val kannSpam = actionsEnabled && comment.status != CommentStatus.SPAM

    val state = rememberSwipeToDismissBoxState()

    // Die Aktion hängt am abgeschlossenen Wischen, nicht an
    // confirmValueChange: Das wird während einer Geste mehrfach aufgerufen,
    // und die Moderation lief dadurch doppelt. Ein Test hält das fest.
    LaunchedEffect(state.currentValue) {
        when (state.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> onModerate(ModerationAction.Approve)
            SwipeToDismissBoxValue.EndToStart -> onModerate(ModerationAction.MarkAsSpam)
            SwipeToDismissBoxValue.Settled -> return@LaunchedEffect
        }
        // Zurück in die Ruhelage, statt weggewischt stehen zu bleiben. Um das
        // Verschwinden kümmert sich die Liste selbst - der Kommentar wechselt
        // den Status und fällt aus dem Filter. Bliebe die Karte weg, stünde
        // bei einem Fehlschlag eine leere Stelle da.
        state.reset()
    }

    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = kannGenehmigen,
        enableDismissFromEndToStart = kannSpam,
        backgroundContent = { SwipeBackground(state.targetValue) },
        modifier = modifier,
    ) {
        CommentCard(
            comment = comment,
            signals = signals,
            teamRole = teamRole,
            showAvatar = showAvatar,
            actionsEnabled = actionsEnabled,
            onOpen = onOpen,
            onModerate = onModerate,
            onReply = onReply,
        )
    }
}

/** Was unter der Karte sichtbar wird, während gewischt wird. */
@Composable
private fun SwipeBackground(target: SwipeToDismissBoxValue) {
    val aussehen = when (target) {
        SwipeToDismissBoxValue.StartToEnd -> SwipeAussehen(
            MaterialTheme.colorScheme.primaryContainer,
            Icons.Default.CheckCircle,
            stringResource(R.string.action_approve),
            Alignment.CenterStart,
        )

        SwipeToDismissBoxValue.EndToStart -> SwipeAussehen(
            MaterialTheme.colorScheme.errorContainer,
            Icons.Default.Warning,
            stringResource(R.string.action_spam),
            Alignment.CenterEnd,
        )

        SwipeToDismissBoxValue.Settled -> SwipeAussehen(
            Color.Transparent,
            null,
            "",
            Alignment.Center,
        )
    }

    Box(
        contentAlignment = aussehen.ausrichtung,
        modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.medium)
            .background(aussehen.farbe)
            .padding(horizontal = 24.dp),
    ) {
        aussehen.symbol?.let { symbol ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = symbol,
                    // Der Text daneben sagt dasselbe.
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(text = aussehen.text, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private data class SwipeAussehen(
    val farbe: Color,
    val symbol: ImageVector?,
    val text: String,
    val ausrichtung: Alignment,
)
