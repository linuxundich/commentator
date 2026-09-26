package de.christophlangner.commentator.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Theme der App nach Material 3 Expressive.
 *
 * Folgt standardmäßig der Systemeinstellung für Hell/Dunkel und übernimmt ab
 * Android 12 die Farben des Benutzers (Dynamic Color). Auf älteren Geräten
 * greift die eigene Palette.
 *
 * Form, Schrift und Bewegung folgen Material 3 Expressive
 * ([CommentatorShapes], [CommentatorTypography], [Bewegung]).
 *
 * [MaterialExpressiveTheme] statt `MaterialTheme`: Es trägt das expressive
 * Bewegungsschema in die Komposition, und damit federn auch die
 * *mitgelieferten* Komponenten - Marken, Schalter, Blätter, Dialoge. Über
 * eigene Animationen allein wäre das nicht zu erreichen; jede Material-
 * Komponente liest ihr Schema aus dem Theme.
 *
 * `motionScheme` wird ausdrücklich gesetzt, obwohl dieser Aufruf es ohnehin
 * als Vorgabe hätte: So steht im Code, was gilt, statt es aus der Wahl des
 * Einstiegspunkts folgern zu müssen.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CommentatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = CommentatorShapes,
        typography = CommentatorTypography,
        content = content,
    )
}
