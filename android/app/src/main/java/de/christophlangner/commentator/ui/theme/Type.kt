package de.christophlangner.commentator.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typografie nach Material 3 Expressive.
 *
 * Expressive arbeitet mit Betonung: Überschriften und Beschriftungen tragen
 * mehr Gewicht, damit die Gliederung einer Fläche schon beim Überfliegen
 * erkennbar ist. Der Fließtext bleibt davon unberührt - er wird gelesen, nicht
 * überflogen, und zusätzliches Gewicht würde ihn nur enger wirken lassen.
 *
 * Es werden keine eigenen Schriften mitgeliefert, damit die Systemschrift und
 * die Schriftgrößen-Einstellung des Benutzers greifen. Die Betonung kommt
 * deshalb aus dem Gewicht, nicht aus einer anderen Schrift.
 *
 * Material 3 kennt in Fassung 1.4.0 keine `*Emphasized`-Rollen; die Betonung
 * ist hier von Hand gesetzt.
 */
private val default = Typography()

val CommentatorTypography = default.copy(
    // Betont: Titel und Überschriften tragen die Gliederung.
    headlineSmall = default.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = default.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = default.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = default.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    // Abschnittsüberschriften in den Einstellungen. Sie stehen klein und
    // farbig da und brauchen Gewicht, um als Überschrift zu wirken.
    titleSmall = default.titleSmall.copy(fontWeight = FontWeight.SemiBold),

    // Zurückhaltend: Der Kommentartext selbst. Nur etwas mehr Zeilenabstand,
    // weil hier lange Absätze gelesen werden.
    bodyLarge = default.bodyLarge.copy(lineHeight = 26.sp),
    bodyMedium = default.bodyMedium.copy(lineHeight = 22.sp),

    // Beschriftungen auf Schaltflächen und Marken. In Expressive sind sie
    // Träger der Bedienbarkeit und dürfen kräftiger stehen als der Text.
    labelLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp,
    ),
    labelMedium = default.labelMedium.copy(fontWeight = FontWeight.Medium),
)
