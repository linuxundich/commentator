package de.christophlangner.commentator.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Ausweichpalette für Geräte ohne Dynamic Color (vor Android 12) oder wenn
 * der Benutzer sie abschaltet. Abgeleitet vom Grün des App-Symbols.
 */
private val Green40 = Color(0xFF196D3C)
private val Green90 = Color(0xFFA7F2C0)
private val Green10 = Color(0xFF00210F)
private val Green30 = Color(0xFF00522B)
private val Green80 = Color(0xFF8BD5A5)

private val Slate40 = Color(0xFF4F6354)
private val Slate90 = Color(0xFFD1E8D5)
private val Slate10 = Color(0xFF0C1F13)
private val Slate80 = Color(0xFFB6CCBA)
private val Slate30 = Color(0xFF374B3C)

private val Sand40 = Color(0xFF7B5800)
private val Sand90 = Color(0xFFFFDEA6)
private val Sand10 = Color(0xFF271900)
private val Sand80 = Color(0xFFF5BD48)
private val Sand30 = Color(0xFF5C4100)

val LightColors = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = Green90,
    onPrimaryContainer = Green10,
    secondary = Slate40,
    onSecondary = Color.White,
    secondaryContainer = Slate90,
    onSecondaryContainer = Slate10,
    tertiary = Sand40,
    onTertiary = Color.White,
    tertiaryContainer = Sand90,
    onTertiaryContainer = Sand10,
)

val DarkColors = darkColorScheme(
    primary = Green80,
    onPrimary = Green10,
    primaryContainer = Green30,
    onPrimaryContainer = Green90,
    secondary = Slate80,
    onSecondary = Slate10,
    secondaryContainer = Slate30,
    onSecondaryContainer = Slate90,
    tertiary = Sand80,
    onTertiary = Sand10,
    tertiaryContainer = Sand30,
    onTertiaryContainer = Sand90,
)
