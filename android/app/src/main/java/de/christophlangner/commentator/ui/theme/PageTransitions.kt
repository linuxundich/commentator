package de.christophlangner.commentator.ui.theme

import androidx.activity.BackEventCompat
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

/**
 * Seitenwechsel wie in der PlanetFOSS-App, die ihn von Moshidon und der
 * offiziellen Mastodon-App hat.
 *
 * Eine neue Seite blendet ein und rückt dabei nur ein kurzes Stück nach
 * (100 dp, nicht die ganze Breite). Sie deckt die Seite darunter zu, die an
 * ihrem Platz bleibt. Zurück blendet die obere Seite in 200 ms aus und gleitet
 * dasselbe kurze Stück zur Seite; die Seite darunter ist einfach da. Kurzer
 * Weg und keine Bewegung darunter lassen den Wechsel schnell wirken. Die
 * Zurück-Geste bewegt die Seite genauso unter dem Finger.
 *
 * Bewusst feste Dauern statt der Federn aus [Bewegung]: Navigation spult die
 * Übergänge bei der Zurück-Geste anhand der Fingerposition vor und zurück.
 * Das geht nur mit einer Dauer, eine Feder hat keine.
 *
 * [shiftPx] sind 100 dp in Pixeln, in Rechts-nach-links-Layouts negativ.
 */
class PageTransitions(private val shiftPx: Int) {

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(tween(ENTER_MS, easing = Ease)) +
            slideInHorizontally(tween(ENTER_MS, easing = Ease)) { shiftPx }
    }

    /** Die Seite darunter bleibt stehen und wird zugedeckt. */
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        ExitTransition.KeepUntilTransitionsFinished
    }

    /** Zurück: Die Seite darunter ist einfach da … */
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        EnterTransition.None
    }

    /** … und die obere blendet aus und gleitet das kurze Stück weg. */
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOff(shiftPx)
    }

    /**
     * Die Zurück-Geste. Von sich aus schrumpft Navigation die Seite auf 70 %,
     * in jeder App gleich. Hier folgt sie dem Finger das kurze Stück und
     * blendet aus, weg von dem Rand, an dem die Geste begann.
     */
    val predictivePopEnter:
        AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> EnterTransition = { popEnter() }

    val predictivePopExit:
        AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> ExitTransition = { edge ->
            slideOff(if (edge == BackEventCompat.EDGE_RIGHT) -shiftPx else shiftPx)
        }

    private fun slideOff(px: Int) =
        fadeOut(tween(EXIT_MS, easing = Ease)) +
            slideOutHorizontally(tween(EXIT_MS, easing = Ease)) { px }

    private companion object {
        /** CSS `ease`: schneller Anfang, lange weiche Landung. */
        val Ease = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
        const val ENTER_MS = 300
        const val EXIT_MS = 200
    }
}
