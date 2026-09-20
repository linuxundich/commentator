package de.christophlangner.commentator.ui.common

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Sperrt Screenshots und die Vorschau in der App-Übersicht, solange
 * [enabled] gilt.
 *
 * Eigene Komponente statt eines Effekts mitten im Bildschirm, weil hier eine
 * Sicherheitszusage steckt: Ein Application Password darf nicht in der
 * Galerie oder im Recents-Screen landen. Als eigenständige Komponente lässt
 * sich das auch prüfen - siehe `ScreenshotProtectionTest`.
 *
 * Das Flag wird nur gesetzt, wenn es gebraucht wird, und beim Verlassen
 * wieder entfernt. Andernfalls bliebe die ganze App unfotografierbar, was für
 * alles außer dem Passwortfeld unnötig wäre.
 */
@Composable
fun ScreenshotProtection(enabled: Boolean) {
    val context = LocalContext.current

    DisposableEffect(enabled) {
        val window = (context as? Activity)?.window

        if (enabled) {
            window?.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }

        onDispose {
            if (enabled) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}
