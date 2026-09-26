package de.christophlangner.commentator.ui.inbox

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.christophlangner.commentator.R
import de.christophlangner.commentator.domain.model.Comment
import de.christophlangner.commentator.domain.model.CommentSignals
import de.christophlangner.commentator.domain.model.CommentStatus
import de.christophlangner.commentator.domain.model.ModerationAction
import de.christophlangner.commentator.domain.model.RoleStyle
import de.christophlangner.commentator.domain.model.TeamRole
import kotlin.math.abs

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
    roleStyle: RoleStyle,
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
        backgroundContent = { SwipeBackground(state) },
        modifier = modifier,
    ) {
        CommentCard(
            comment = comment,
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

/**
 * Was unter der Karte sichtbar wird, während gewischt wird.
 *
 * Die Richtung stammt aus `dismissDirection` und nicht aus `targetValue`:
 * Letzteres bleibt `Settled`, solange die Wischschwelle nicht überschritten
 * ist – der Hinweis wäre damit erst aufgetaucht, wenn die Entscheidung ohnehin
 * gefallen war.
 *
 * Symbol und Beschriftung erscheinen erst, wenn sie **vollständig** neben die
 * Karte passen. Beim Nachstellen am Gerät zeigte sich sonst bei kurzem Ziehen
 * ein halbes Symbol und ein mitten im Wort abgeschnittenes Wort – ein
 * angeschnittenes Zeichen sagt weniger als gar keines.
 */
@Composable
private fun SwipeBackground(state: SwipeToDismissBoxState) {
    val aussehen = when (state.dismissDirection) {
        SwipeToDismissBoxValue.StartToEnd -> SwipeAussehen(
            farbe = MaterialTheme.colorScheme.primaryContainer,
            symbol = Icons.Default.CheckCircle,
            text = stringResource(R.string.action_approve),
            ausrichtung = Alignment.CenterStart,
            ursprung = TransformOrigin(0f, 0.5f),
        )

        SwipeToDismissBoxValue.EndToStart -> SwipeAussehen(
            farbe = MaterialTheme.colorScheme.errorContainer,
            symbol = Icons.Default.Warning,
            text = stringResource(R.string.action_spam),
            ausrichtung = Alignment.CenterEnd,
            ursprung = TransformOrigin(1f, 0.5f),
        )

        // In Ruhelage liegt die Karte deckend darüber; hier ist nichts zu tun.
        SwipeToDismissBoxValue.Settled -> null
    } ?: return

    // Ob ein Loslassen die Aktion jetzt auslösen würde.
    val scharf = state.targetValue != SwipeToDismissBoxValue.Settled

    val haptik = LocalHapticFeedback.current
    LaunchedEffect(scharf) {
        // Ein kurzer Impuls genau in dem Moment, in dem die Schwelle fällt.
        // Ohne ihn bleibt beim Ziehen offen, ob es schon weit genug ist.
        if (scharf) {
            haptik.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
    }

    // Sichtbare Bestätigung derselben Schwelle - Haptik allein hilft nicht
    // weiter, wo sie abgeschaltet ist oder nicht wahrgenommen wird.
    val betonung by animateFloatAsState(
        targetValue = if (scharf) BETONT else 1f,
        label = "Wischbetonung",
    )

    val dichte = LocalDensity.current
    val randPx = with(dichte) { RAND.toPx() }
    val abstandPx = with(dichte) { ABSTAND.toPx() }
    val ueberblendPx = with(dichte) { UEBERBLENDUNG.toPx() }

    // Gemessen statt geschätzt: Wie breit die Beschriftung ausfällt, hängt an
    // Sprache und eingestellter Schriftgröße.
    var symbolBreite by remember { mutableFloatStateOf(0f) }
    var textBreite by remember { mutableFloatStateOf(0f) }

    Box(
        contentAlignment = aussehen.ausrichtung,
        modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.medium)
            .background(aussehen.farbe)
            .padding(horizontal = RAND),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.graphicsLayer {
                scaleX = betonung
                scaleY = betonung
                transformOrigin = aussehen.ursprung
            },
        ) {
            Icon(
                imageVector = aussehen.symbol,
                // Der Text daneben sagt dasselbe.
                contentDescription = null,
                modifier = Modifier
                    .size(SYMBOLGROESSE)
                    .onSizeChanged { symbolBreite = it.width.toFloat() }
                    .graphicsLayer {
                        val grad = einblendgrad(
                            freigelegt = state.freigelegteBreite(),
                            ab = randPx + symbolBreite,
                            ueberblendung = ueberblendPx,
                        )
                        alpha = grad
                        // Leicht hineinwachsen statt hart erscheinen.
                        scaleX = KLEIN + (1f - KLEIN) * grad
                        scaleY = scaleX
                    },
            )
            Spacer(Modifier.width(ABSTAND))
            Text(
                text = aussehen.text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier
                    .onSizeChanged { textBreite = it.width.toFloat() }
                    .graphicsLayer {
                        alpha = einblendgrad(
                            freigelegt = state.freigelegteBreite(),
                            ab = randPx + symbolBreite + abstandPx + textBreite,
                            ueberblendung = ueberblendPx,
                        )
                    },
            )
        }
    }
}

/**
 * Wie weit ein Zeichen eingeblendet ist: 0 bedeutet unsichtbar, 1 voll da.
 *
 * Unterhalb von [ab] ist nichts zu sehen. [ab] ist die Breite, die das Zeichen
 * gerade so neben der Karte braucht; über [ueberblendung] wird es danach weich
 * aufgeblendet, damit es nicht aufpoppt.
 */
internal fun einblendgrad(freigelegt: Float, ab: Float, ueberblendung: Float): Float = when {
    ueberblendung <= 0f -> if (freigelegt >= ab) 1f else 0f
    else -> ((freigelegt - ab) / ueberblendung).coerceIn(0f, 1f)
}

/**
 * Wie viel neben der Karte freiliegt.
 *
 * `requireOffset` wirft, solange die Anker der Box nicht stehen. In der
 * Zeichenphase stehen sie – die Absicherung deckt nur den allerersten Rahmen
 * ab, in dem ohnehin nichts zu sehen wäre.
 */
private fun SwipeToDismissBoxState.freigelegteBreite(): Float =
    runCatching { abs(requireOffset()) }.getOrDefault(0f)

private data class SwipeAussehen(
    val farbe: Color,
    val symbol: ImageVector,
    val text: String,
    val ausrichtung: Alignment,
    val ursprung: TransformOrigin,
)

private val RAND = 24.dp
private val ABSTAND = 8.dp
private val SYMBOLGROESSE = 22.dp

/** Strecke, über die ein Zeichen aufblendet, sobald es hineinpasst. */
private val UEBERBLENDUNG = 24.dp

/** Anfangsgröße des hineinwachsenden Symbols. */
private const val KLEIN = 0.6f

/** Vergrößerung, sobald das Loslassen die Aktion auslösen würde. */
private const val BETONT = 1.12f
