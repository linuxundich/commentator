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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.christophlangner.commentator.R
import de.christophlangner.commentator.core.time.RelativeTime
import de.christophlangner.commentator.domain.model.Comment
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.model.CommentSignals
import de.christophlangner.commentator.domain.model.ModerationAction
import de.christophlangner.commentator.domain.model.TeamRole
import de.christophlangner.commentator.ui.common.CommentAvatar
import de.christophlangner.commentator.ui.common.SignalChips
import de.christophlangner.commentator.ui.common.StatusChip

/**
 * Ein Kommentar in der Liste.
 *
 * Die wichtigsten Moderationsentscheidungen sind direkt erreichbar, ohne den
 * Kommentar öffnen zu müssen - das ist der eigentliche Zweck der App.
 */
@Composable
fun CommentCard(
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
    val openLabel = stringResource(R.string.cd_open_comment, comment.authorName)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = openLabel, onClick = onOpen),
        colors = CardDefaults.cardColors(
            // Beitraege des Teams heben sich durch den Grundton ab, nicht nur
            // durch eine Marke: In einer langen Liste erkennt man sie so schon
            // beim Ueberfliegen.
            containerColor = when {
                teamRole == null -> MaterialTheme.colorScheme.surfaceContainerLow
                // Administratoren in Rot: Sie koennen alles aendern, eine
                // Wortmeldung von dort wiegt schwerer als die eines
                // Redakteurs.
                teamRole.isAdministrator -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.secondaryContainer
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showAvatar) {
                    CommentAvatar(url = comment.avatarUrl, size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = comment.authorName.ifBlank {
                            stringResource(R.string.comment_author_unknown)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = RelativeTime.relative(comment.date, LocalResources.current).toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics {
                            contentDescription = RelativeTime.absolute(comment.date)
                        },
                    )
                }

                teamRole?.let { rolle ->
                    TeamBadge(rolle)
                    Spacer(Modifier.width(8.dp))
                }
                StatusChip(status = comment.status)
            }

            Text(
                text = comment.contentPlain,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp),
            )

            comment.postTitle?.let { title ->
                Text(
                    text = stringResource(R.string.comment_on_post, title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            SignalChips(
                signals = signals,
                modifier = Modifier.padding(top = 8.dp),
            )

            CardActions(
                comment = comment,
                enabled = actionsEnabled,
                onModerate = onModerate,
                onReply = onReply,
            )
        }
    }
}

/**
 * Marke mit der Rolle des Verfassers.
 *
 * Sie nennt die Rolle beim Namen statt nur "Team": Zwischen einem
 * Administrator und einem Redakteur besteht ein Unterschied, den die Farbe
 * allein nicht traegt - und fuer Bildschirmleser und bei Farbfehlsichtigkeit
 * ist der Text die eigentliche Information.
 */
@Composable
private fun TeamBadge(role: TeamRole) {
    val hintergrund = if (role.isAdministrator) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.secondary
    }
    val vordergrund = if (role.isAdministrator) {
        MaterialTheme.colorScheme.onError
    } else {
        MaterialTheme.colorScheme.onSecondary
    }

    Surface(shape = MaterialTheme.shapes.small, color = hintergrund) {
        Text(
            text = role.name.ifBlank { stringResource(R.string.comment_from_team) },
            style = MaterialTheme.typography.labelSmall,
            color = vordergrund,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun CardActions(
    comment: Comment,
    enabled: Boolean,
    onModerate: (ModerationAction) -> Unit,
    onReply: () -> Unit,
) {
    // Eine hervorgehobene Hauptaktion, der Rest als Symbole. Vorher standen
    // vier beschriftete Schaltflaechen da, die auf zwei Zeilen umbrachen -
    // jede Karte wurde dadurch so hoch, dass kaum drei Kommentare auf den
    // Bildschirm passten.
    val approved = comment.status == CommentStatus.APPROVED

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        if (approved) {
            FilledTonalButton(onClick = onReply, enabled = enabled) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_reply))
            }
        } else {
            FilledTonalButton(
                onClick = { onModerate(ModerationAction.Approve) },
                enabled = enabled,
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_approve))
            }
        }

        Spacer(Modifier.weight(1f))

        if (!approved) {
            CardIconAction(
                icon = Icons.AutoMirrored.Filled.Send,
                label = stringResource(R.string.action_reply),
                enabled = enabled,
                onClick = onReply,
            )
        }
        if (comment.status != CommentStatus.SPAM) {
            CardIconAction(
                icon = Icons.Default.Warning,
                label = stringResource(R.string.action_spam),
                enabled = enabled,
                onClick = { onModerate(ModerationAction.MarkAsSpam) },
            )
        }
        if (comment.status != CommentStatus.TRASH) {
            CardIconAction(
                icon = Icons.Default.Delete,
                label = stringResource(R.string.action_trash),
                enabled = enabled,
                onClick = { onModerate(ModerationAction.Delete(permanent = false)) },
            )
        }
    }
}

/**
 * Symbolknopf mit gesprochener Beschriftung.
 *
 * Ohne Text auf dem Bildschirm, aber mit contentDescription: Fuer
 * Bildschirmleser bleibt die Aktion benannt, und der Knopf behaelt die voll
 * bedienbare Groesse von IconButton.
 */
@Composable
private fun CardIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(imageVector = icon, contentDescription = label)
    }
}
