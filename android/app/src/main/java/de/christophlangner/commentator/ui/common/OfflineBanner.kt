package de.christophlangner.commentator.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.christophlangner.commentator.R
import de.christophlangner.commentator.core.time.RelativeTime
import java.time.Instant

/**
 * Macht sichtbar, woher die angezeigten Daten stammen.
 *
 * Die Vorgabe verlangt, lokale und frisch geladene Daten zu unterscheiden.
 * Deshalb nennt dieses Band den Zeitpunkt der letzten erfolgreichen
 * Aktualisierung, sobald die Verbindung fehlt.
 */
@Composable
fun OfflineBanner(
    isOffline: Boolean,
    lastSync: Instant?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = isOffline, modifier = modifier) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = if (lastSync == null) {
                        stringResource(R.string.offline_no_data)
                    } else {
                        stringResource(
                            R.string.offline_last_sync,
                            RelativeTime.relative(lastSync, LocalResources.current).toString(),
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/** Hinweisband für eine abgelehnte oder abgelaufene Anmeldung. */
@Composable
fun SessionInvalidBanner(
    visible: Boolean,
    onReauthenticate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = visible, modifier = modifier) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.session_invalid_banner),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onReauthenticate) {
                    Text(stringResource(R.string.action_sign_in_again))
                }
            }
        }
    }
}
