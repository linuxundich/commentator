package de.christophlangner.commentator.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.christophlangner.commentator.R

/**
 * Platzhalterkarten für den Erstaufbau der Liste.
 *
 * Statt eines Kreises im Leeren steht schon die Form da, die gleich mit
 * Inhalt gefüllt wird. Das ist ruhiger anzusehen, und die Liste springt beim
 * Eintreffen der Daten nicht, weil die Höhen bereits stimmen.
 *
 * Für Bildschirmleser bleibt es eine einzige Aussage – „wird geladen“ –
 * statt einer Handvoll bedeutungsloser Rechtecke.
 */
@Composable
fun CommentSkeletonList(
    modifier: Modifier = Modifier,
    count: Int = 4,
) {
    val label = stringResource(R.string.state_loading)
    val transition = rememberInfiniteTransition(label = "Platzhalter")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            // Langsam und weich: Ein schnelles Blinken zöge die Aufmerksamkeit
            // auf die Wartezeit, statt sie vergessen zu lassen.
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "Pulsieren",
    )

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
    ) {
        items(count) { index ->
            SkeletonCard(
                alpha = pulse,
                // Unterschiedlich lange Schlusszeilen: Gleich lange Balken
                // sehen nach Tabelle aus, nicht nach Text.
                lastLineFraction = if (index % 2 == 0) 0.6f else 0.8f,
            )
        }
    }
}

@Composable
private fun SkeletonCard(alpha: Float, lastLineFraction: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .shimmer(alpha),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Bar(width = 140.dp, alpha = alpha)
                    Spacer(Modifier.height(6.dp))
                    Bar(width = 80.dp, height = 12.dp, alpha = alpha)
                }
            }
            Spacer(Modifier.height(14.dp))
            Bar(fraction = 1f, alpha = alpha)
            Spacer(Modifier.height(8.dp))
            Bar(fraction = lastLineFraction, alpha = alpha)
        }
    }
}

/** Ein Balken, entweder mit fester Breite oder als Anteil der Zeile. */
@Composable
private fun Bar(
    alpha: Float,
    width: Dp? = null,
    fraction: Float? = null,
    height: Dp = 14.dp,
) {
    val form = Modifier
        .height(height)
        .clip(RoundedCornerShape(6.dp))
        .shimmer(alpha)

    Spacer(if (width != null) form.width(width) else form.fillMaxWidth(fraction ?: 1f))
}

@Composable
private fun Modifier.shimmer(alpha: Float): Modifier =
    this.alpha(alpha).background(MaterialTheme.colorScheme.surfaceContainerHighest)
