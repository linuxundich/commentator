package de.christophlangner.commentator.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.christophlangner.commentator.R
import de.christophlangner.commentator.domain.model.Comment
import de.christophlangner.commentator.domain.model.CommentSignals
import de.christophlangner.commentator.domain.model.ModerationAction
import de.christophlangner.commentator.domain.model.RoleStyle
import de.christophlangner.commentator.domain.model.TeamRole
import de.christophlangner.commentator.domain.model.ThreadEntry
import de.christophlangner.commentator.ui.common.StatusChip

/** Einrückung je Stufe im Faden. */
private val EINRUECKUNG = 16.dp

/** Stärke der senkrechten Linie, die eine Antwort mit dem Bezug verbindet. */
private val LINIENBREITE = 3.dp

/**
 * Ein Eintrag der Liste, an seinem Platz im Gesprächsfaden.
 *
 * Eingerückt und mit einer senkrechten Linie, sobald er eine Antwort ist –
 * ohne die sähe eine Antwort wie ein eigenständiger Kommentar aus. Dieselbe
 * Gestaltung wie im Antwortfaden der Detailansicht, damit man die Einrückung
 * nicht zweimal lernen muss.
 */
@Composable
fun ThreadItem(
    entry: ThreadEntry,
    signals: CommentSignals,
    teamRole: TeamRole?,
    roleStyle: RoleStyle,
    showAvatar: Boolean,
    actionsEnabled: Boolean,
    onOpen: () -> Unit,
    onModerate: (ModerationAction) -> Unit,
    onReply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val eingerueckt = entry.depth > 0
    val linienfarbe = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = EINRUECKUNG * entry.depth)
            // Die Linie wird gezeichnet, nicht gelegt: Als eigenes Element
            // bräuchte sie die Höhe der Karte daneben, und eine intrinsische
            // Messung dreht sich um eine SwipeToDismissBox endlos - ein Test
            // lief dabei in die Zeitüberschreitung.
            .drawBehind {
                if (!eingerueckt) return@drawBehind
                val breite = LINIENBREITE.toPx()
                drawRoundRect(
                    color = linienfarbe,
                    size = Size(breite, size.height),
                    cornerRadius = CornerRadius(breite / 2),
                )
            }
            .padding(start = if (eingerueckt) LINIENBREITE + 12.dp else 0.dp),
    ) {
        if (entry.isContext) {
            ContextCard(comment = entry.comment, onOpen = onOpen)
        } else {
            SwipeableCommentCard(
                comment = entry.comment,
                signals = signals,
                teamRole = teamRole,
                roleStyle = roleStyle,
                showAvatar = showAvatar,
                actionsEnabled = actionsEnabled,
                onOpen = onOpen,
                onModerate = onModerate,
                onReply = onReply,
            )
        }
    }
}

/**
 * Der Kommentar, auf den geantwortet wurde.
 *
 * Gedämpft und ohne Schaltflächen: Er gehört nicht zum gewählten Filter und
 * steht nur da, damit die Antwort darunter nicht ohne die Frage dasteht. Wer
 * ihn doch moderieren will, tippt ihn an und landet in der Detailansicht.
 */
@Composable
private fun ContextCard(comment: Comment, onOpen: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = stringResource(R.string.cd_open_comment, comment.authorName),
                onClick = onOpen,
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.authorName.ifBlank {
                        stringResource(R.string.comment_author_unknown)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                StatusChip(status = comment.status)
            }
            Text(
                text = comment.contentPlain,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
