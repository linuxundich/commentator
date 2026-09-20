package de.christophlangner.commentator.data.system

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import de.christophlangner.commentator.MainActivity
import de.christophlangner.commentator.domain.model.AppIcon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schaltet zwischen den Symbolvarianten um.
 *
 * Maßgeblich ist der Zustand im PackageManager, nicht eine eigene
 * Einstellung. Damit kann beides nicht auseinanderlaufen - etwa wenn die
 * App-Daten gelöscht werden, der Systemzustand aber bestehen bleibt.
 */
@Singleton
class AppIconManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val _current = MutableStateFlow(readCurrent())
    val current: StateFlow<AppIcon> = _current.asStateFlow()

    fun select(icon: AppIcon) {
        if (icon == _current.value) return

        // Reihenfolge ist wichtig: Erst das neue Symbol einschalten, dann die
        // übrigen aus. Andersherum gäbe es einen Moment, in dem gar kein
        // Startsymbol eingeschaltet ist - die App verschwände dann aus dem
        // Startbildschirm, bei manchen Herstellern dauerhaft.
        setEnabled(icon, enabled = true)
        AppIcon.entries.filter { it != icon }.forEach { setEnabled(it, enabled = false) }

        _current.value = icon
    }

    private fun setEnabled(icon: AppIcon, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            componentFor(icon),
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            // Ohne dieses Flag beendet Android den Prozess sofort - mitten in
            // der Bedienung der Einstellungen.
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun readCurrent(): AppIcon =
        AppIcon.entries.firstOrNull(::isEnabled) ?: AppIcon.DEFAULT

    private fun isEnabled(icon: AppIcon): Boolean =
        when (context.packageManager.getComponentEnabledSetting(componentFor(icon))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            // DEFAULT heißt „wie im Manifest" - dort ist die Standardvariante
            // eingeschaltet und jede andere ausgeschaltet.
            else -> icon == AppIcon.DEFAULT
        }

    /**
     * Der Klassenname des Alias folgt der `namespace` des Moduls, die
     * installierte Paketkennung dagegen der `applicationId` - im Debug-Build
     * mit dem Zusatz `.debug`. Beides ist also nicht dasselbe, und aus der
     * Paketkennung den Klassennamen zusammenzusetzen ginge dort schief.
     * Deshalb wird der Namensraum aus einer echten Klasse abgeleitet.
     */
    private fun componentFor(icon: AppIcon) =
        ComponentName(context.packageName, NAMESPACE + icon.aliasName)

    private companion object {
        val NAMESPACE: String = MainActivity::class.java.name.substringBeforeLast('.')
    }
}
