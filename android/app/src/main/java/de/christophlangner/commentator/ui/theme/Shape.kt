package de.christophlangner.commentator.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Formenskala nach Material 3 Expressive.
 *
 * Expressive behandelt die Form wie die Schrift: nicht ein Radius für alles,
 * sondern eine Skala mit Spannweite. Rund wird dort, wo eine Fläche für sich
 * steht - Dialoge, Blätter, Karten -, und zurückhaltend dort, wo viele
 * Elemente nebeneinander liegen.
 *
 * Die Werte liegen über den Vorgaben von Material 3, aber nicht am oberen
 * Ende der Skala. Commentator ist ein Arbeitswerkzeug mit langen Listen: Jeder
 * Millimeter Radius kostet nutzbare Breite, und bei stark gerundeten Ecken
 * rückt der Text von den Rändern ab, ohne dass mehr zu lesen wäre.
 *
 * `largeIncreased`, `extraLargeIncreased` und `extraExtraLarge` aus der
 * Expressive-Skala sind in material3 1.4.0 `internal` - sie lassen sich weder
 * lesen noch zuverlässig setzen. Die Komponenten, die sie verwenden, greifen
 * deshalb auf ihre Vorgaben zurück; die sind in dieser Fassung ohnehin schon
 * die expressiven.
 */
val CommentatorShapes = Shapes(
    // Marken und kleine Flächen. Knapp über der Vorgabe: Sie stehen zu mehreren
    // in einer Zeile, und ein großer Radius ließe sie ineinanderlaufen.
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    // Karten der Liste. Der Hauptarbeitsbereich - hier wirkt der Radius am
    // stärksten auf die nutzbare Breite.
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    // Dialoge und Blätter. Sie stehen allein vor abgedunkeltem Hintergrund und
    // dürfen deshalb die volle Spannweite zeigen.
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Formen, die keine Rolle der Skala besetzen.
 *
 * Eigene Konstanten statt Zahlen an der Verwendungsstelle: So steht die
 * Begründung einmal hier, und zwei Stellen mit derselben Aufgabe bekommen
 * nicht aus Versehen verschiedene Radien.
 */
object CommentatorFormen {

    /**
     * Ein Element in einer verbundenen Gruppe, außen.
     *
     * Die Filterleiste ist in Expressive eine zusammenhängende Gruppe: Sie
     * wirkt als ein Ding, nicht als fünf Marken. Außen rund, innen fast
     * gerade - das hält die Gruppe zusammen.
     */
    val GruppeAussen = 20.dp

    /** Dieselbe Gruppe an den Innenkanten. */
    val GruppeInnen = 6.dp

    /**
     * Dasselbe Element unter dem Finger.
     *
     * Zwischen Ruhe und Auswahl: Der Druck kündigt die Auswahl an, ohne sie
     * vorwegzunehmen.
     */
    val GruppeGedrueckt = 14.dp

    /**
     * Das ausgewählte Element der Gruppe.
     *
     * Deutlich runder als seine Nachbarn. In Expressive trägt die Auswahl ihre
     * Form, nicht nur ihre Farbe - das bleibt auch ohne Farbwahrnehmung
     * erkennbar.
     */
    val GruppeGewaehlt = 20.dp
}
