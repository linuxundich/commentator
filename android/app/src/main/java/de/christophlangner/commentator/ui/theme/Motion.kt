package de.christophlangner.commentator.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Bewegung nach Material 3 Expressive.
 *
 * Expressive ersetzt festgelegte Dauern durch Federn. Der Unterschied ist
 * nicht bloß Geschmack: Eine Feder kennt die Geschwindigkeit, mit der eine
 * Bewegung ankommt, und kann daraus weiterlaufen. Eine Kurve über 200 ms kann
 * das nicht - sie beginnt immer bei Null, auch wenn der Finger das Element
 * gerade noch geschoben hat.
 *
 * Die Unterscheidung, auf die es ankommt:
 *
 * * **räumlich** - alles, was seinen Platz oder seine Größe ändert. Diese
 *   Federn schwingen leicht über, und genau das lässt eine Bewegung
 *   körperlich wirken.
 * * **Effekt** - alles, was seine Deckkraft oder Farbe ändert. Diese Federn
 *   schwingen *nicht* über. Eine überschwingende Deckkraft müsste über 100 %
 *   hinaus; sichtbar wäre nur ein Flackern am Ende.
 *
 * Wer eine Farbe mit einer räumlichen Feder animiert, bekommt deshalb ein
 * Zucken - und wer eine Position mit einer Effektfeder animiert, bekommt eine
 * Bewegung, die sich totstellt.
 *
 * Die Werte kommen aus dem Theme ([MaterialTheme.motionScheme]) und nicht aus
 * Konstanten hier. Deshalb wirkt ein Wechsel des Schemas überall, und es gibt
 * keine Stelle, die aus Versehen bei ihrer eigenen Zeitangabe bleibt. Gesetzt
 * wird das expressive Schema in [CommentatorTheme].
 *
 * Diese Hülle ist trotzdem sinnvoll: Sie benennt auf Deutsch, welche Feder
 * wofür gedacht ist, und hält die Entscheidung räumlich-oder-Effekt an einer
 * Stelle fest statt an jeder Verwendungsstelle neu.
 *
 * Die Funktionen sind generisch, weil dieselbe Feder für ganz verschiedene
 * Werte gebraucht wird: `Float` für Deckkraft, `IntSize` für eine wachsende
 * Fläche, `Dp` für einen Abstand. Der Typ ergibt sich an der Verwendungsstelle.
 */
object Bewegung {

    /** Für Platz- und Größenänderungen, der Regelfall. */
    @Composable
    @ReadOnlyComposable
    fun <T> raeumlich(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultSpatialSpec()

    /**
     * Für kurze Wege und unmittelbare Rückmeldung.
     *
     * Etwa die Bestätigung, dass eine Wischgeste weit genug ist: Sie muss
     * innerhalb der Geste ankommen, sonst bestätigt sie etwas, das schon
     * vorbei ist.
     */
    @Composable
    @ReadOnlyComposable
    fun <T> schnellRaeumlich(): FiniteAnimationSpec<T> =
        MaterialTheme.motionScheme.fastSpatialSpec()

    /**
     * Für große Flächen, die kommen und gehen.
     *
     * Ein Blatt oder ein Hinweisband darf sich Zeit nehmen; schnell wirkt dort
     * hektisch, weil sich viel Fläche bewegt.
     */
    @Composable
    @ReadOnlyComposable
    fun <T> langsamRaeumlich(): FiniteAnimationSpec<T> =
        MaterialTheme.motionScheme.slowSpatialSpec()

    /** Für Deckkraft und Farbe - ohne Überschwingen. */
    @Composable
    @ReadOnlyComposable
    fun <T> effekt(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultEffectsSpec()

    /** Wie [effekt], für Ein- und Ausblenden, das nicht auffallen soll. */
    @Composable
    @ReadOnlyComposable
    fun <T> schnellerEffekt(): FiniteAnimationSpec<T> =
        MaterialTheme.motionScheme.fastEffectsSpec()

    /** Wie [effekt], für große Flächen. */
    @Composable
    @ReadOnlyComposable
    fun <T> langsamerEffekt(): FiniteAnimationSpec<T> =
        MaterialTheme.motionScheme.slowEffectsSpec()
}
