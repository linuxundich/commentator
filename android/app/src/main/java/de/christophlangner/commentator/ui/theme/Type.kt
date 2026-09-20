package de.christophlangner.commentator.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typografie auf Basis der Material-3-Standardwerte.
 *
 * Angepasst wird nur, was die Lesbarkeit langer Kommentartexte verbessert:
 * etwas mehr Zeilenabstand im Fließtext. Es werden keine eigenen Schriften
 * mitgeliefert, damit die Systemschrift und die Schriftgrößen-Einstellung des
 * Benutzers greifen.
 */
private val default = Typography()

val CommentatorTypography = default.copy(
    bodyLarge = default.bodyLarge.copy(lineHeight = 26.sp),
    bodyMedium = default.bodyMedium.copy(lineHeight = 22.sp),
    titleMedium = default.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp,
    ),
)
