package de.christophlangner.commentator.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Breite, die die längste der [labels] in [style] tatsächlich braucht.
 *
 * Für Zeilen aus Beschriftung und Wert, deren Beschriftungen untereinander
 * fluchten sollen. Zuvor stand dort eine feste Breite; bei 200 % Schriftgröße
 * wurde aus „Beitrag" dann „Beitr / ag" – ein Umbruch mitten im Wort.
 *
 * Gemessen statt geschätzt: Die nötige Breite hängt an der Schriftgröße, der
 * Schriftart und der Sprache. Ein fester Wert kann nur für einen dieser Fälle
 * stimmen.
 */
@Composable
fun rememberLabelWidth(labels: List<String>, style: TextStyle): Dp {
    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current

    // Neu gemessen wird, wenn sich Beschriftungen, Schriftbild oder die
    // eingestellte Schriftgröße ändern - sonst bliebe eine zu schmale Spalte
    // stehen, bis der Bildschirm neu aufgebaut wird.
    return remember(labels, style, dichte.density, dichte.fontScale) {
        val breiteste = labels.maxOfOrNull { messer.measure(it, style).size.width } ?: return@remember 0.dp
        with(dichte) { breiteste.toDp() }
    }
}
