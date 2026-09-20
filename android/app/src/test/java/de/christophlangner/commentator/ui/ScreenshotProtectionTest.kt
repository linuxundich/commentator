package de.christophlangner.commentator.ui

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import de.christophlangner.commentator.ui.common.ScreenshotProtection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sicherheitseigenschaft, kein Darstellungsdetail: Solange ein Application
 * Password auf dem Schirm stehen kann, darf Android weder einen Screenshot
 * noch eine Vorschau in der App-Übersicht erzeugen.
 *
 * Genauso wichtig ist die Gegenrichtung - bliebe das Flag hängen, wäre die
 * gesamte App unfotografierbar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "de-rDE-w411dp-h891dp")
class ScreenshotProtectionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val isSecure: Boolean
        get() = composeRule.activity.window.attributes.flags and
            WindowManager.LayoutParams.FLAG_SECURE != 0

    @Test
    fun `eingeschaltet sperrt Screenshots`() {
        composeRule.setContent { ScreenshotProtection(enabled = true) }

        assertTrue(isSecure)
    }

    @Test
    fun `ausgeschaltet bleibt die App fotografierbar`() {
        composeRule.setContent { ScreenshotProtection(enabled = false) }

        assertFalse(isSecure)
    }

    @Test
    fun `Wechsel in den geschuetzten Schritt und wieder heraus`() {
        var protected by mutableStateOf(false)
        composeRule.setContent { ScreenshotProtection(enabled = protected) }
        assertFalse(isSecure)

        protected = true
        composeRule.waitForIdle()
        assertTrue("Im Passwortschritt muss das Flag gesetzt sein", isSecure)

        protected = false
        composeRule.waitForIdle()
        assertFalse("Nach dem Verlassen muss das Flag verschwinden", isSecure)
    }
}
