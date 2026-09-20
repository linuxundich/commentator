package de.christophlangner.commentator.ui.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import coil3.compose.AsyncImage
import de.christophlangner.commentator.R
import de.christophlangner.commentator.core.time.RelativeTime
import de.christophlangner.commentator.domain.model.Comment
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.model.ModerationAction
import de.christophlangner.commentator.ui.common.StatusChip

/**
 * Ein Kommentar in der Liste.
 *
 * Die wichtigsten Moderationsentscheidungen sind direkt erreichbar, ohne den
 * Kommentar öffnen zu müssen - das ist der eigentliche Zweck der App.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommentCard(
    comment: Comment,
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showAvatar && comment.avatarUrl != null) {
                    AsyncImage(
                        model = comment.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                    )
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

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                if (comment.status != CommentStatus.APPROVED) {
                    CardAction(
                        icon = Icons.Default.CheckCircle,
                        label = stringResource(R.string.action_approve),
                        enabled = actionsEnabled,
                        onClick = { onModerate(ModerationAction.Approve) },
                    )
                }
                if (comment.status != CommentStatus.SPAM) {
                    CardAction(
                        icon = Icons.Default.Warning,
                        label = stringResource(R.string.action_spam),
                        enabled = actionsEnabled,
                        onClick = { onModerate(ModerationAction.MarkAsSpam) },
                    )
                }
                if (comment.status != CommentStatus.TRASH) {
                    CardAction(
                        icon = Icons.Default.Delete,
                        label = stringResource(R.string.action_trash),
                        enabled = actionsEnabled,
                        onClick = { onModerate(ModerationAction.Delete(permanent = false)) },
                    )
                }
                CardAction(
                    icon = null,
                    label = stringResource(R.string.action_reply),
                    enabled = actionsEnabled,
                    onClick = onReply,
                )
            }
        }
    }
}

@Composable
private fun CardAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, enabled = enabled) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(label)
    }
}
