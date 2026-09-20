package de.christophlangner.commentator.data.system

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import de.christophlangner.commentator.MainActivity
import de.christophlangner.commentator.domain.model.AppIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Das Umschalten des Startsymbols greift in den Systemzustand ein. Geht dabei
 * etwas schief, verschwindet die App aus dem Startbildschirm - deshalb ist
 * hier festgehalten, was gelten muss.
 */
@RunWith(RobolectricTestRunner::class)
class AppIconManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = AppIconManager(context)
    private val packageManager = context.packageManager

    private fun componentOf(icon: AppIcon) = ComponentName(
        context.packageName,
        MainActivity::class.java.name.substringBeforeLast('.') + icon.aliasName,
    )

    private fun stateOf(icon: AppIcon) = packageManager.getComponentEnabledSetting(componentOf(icon))

    @Test
    fun `ohne Zutun gilt die Standardvariante`() {
        assertEquals(AppIcon.DEFAULT, manager.current.value)
    }

    @Test
    fun `Umschalten aktiviert die gewaehlte und deaktiviert die andere`() {
        manager.select(AppIcon.Blue)

        assertEquals(AppIcon.Blue, manager.current.value)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, stateOf(AppIcon.Blue))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, stateOf(AppIcon.Green))
    }

    @Test
    fun `Zuruecklschalten stellt den Ausgangszustand her`() {
        manager.select(AppIcon.Blue)
        manager.select(AppIcon.Green)

        assertEquals(AppIcon.Green, manager.current.value)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, stateOf(AppIcon.Green))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, stateOf(AppIcon.Blue))
    }

    @Test
    fun `die gewaehlte Variante ist nie ausgeschaltet und keine zweite an`() {
        // Das ist die Zusicherung, an der alles hängt: Wäre keine Variante
        // eingeschaltet, verschwände die App aus dem Startbildschirm. Wären es
        // zwei, stünde sie doppelt darin.
        AppIcon.entries.forEach { chosen ->
            manager.select(chosen)

            assertEquals(chosen, manager.current.value)
            assertTrue(
                "Die gewählte Variante darf nicht ausgeschaltet sein",
                stateOf(chosen) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            )
            assertTrue(
                "Keine andere Variante darf eingeschaltet sein",
                AppIcon.entries
                    .filter { stateOf(it) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED }
                    .all { it == chosen },
            )
        }
    }

    @Test
    fun `dieselbe Variante erneut zu waehlen aendert nichts`() {
        manager.select(AppIcon.Green)

        // Green ist der Ausgangszustand, es darf also gar nichts geschrieben
        // werden - sonst flackerte der Startbildschirm ohne Grund.
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, stateOf(AppIcon.Green))
        assertEquals(AppIcon.Green, manager.current.value)
    }

    @Test
    fun `der Klassenname folgt dem Namensraum, nicht der Paketkennung`() {
        // Im Debug-Build trägt die Paketkennung den Zusatz ".debug", der
        // Klassenname des Alias jedoch nicht. Würde man ihn aus der
        // Paketkennung zusammensetzen, zeigte er ins Leere und das Umschalten
        // liefe wirkungslos ins Nichts.
        manager.select(AppIcon.Blue)

        val namespace = MainActivity::class.java.name.substringBeforeLast('.')
        assertTrue(
            "Der Namensraum darf den Zusatz der Paketkennung nicht enthalten",
            !namespace.endsWith(".debug"),
        )
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            packageManager.getComponentEnabledSetting(
                ComponentName(context.packageName, namespace + ".LauncherBlue"),
            ),
        )
    }
}
