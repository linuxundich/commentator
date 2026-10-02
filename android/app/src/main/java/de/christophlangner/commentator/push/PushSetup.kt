package de.christophlangner.commentator.push

import android.app.Activity
import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.ui.common.ErrorTexts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Was die Einstellungen von der Sofortmeldung brauchen.
 *
 * Als Schnittstelle, damit sich der Bildschirm ohne UnifiedPush und ohne
 * Gerät prüfen lässt.
 */
interface PushSetup {

    /** Der letzte Fehler je Blog. */
    val errors: StateFlow<Map<String, AppError>>

    /** Ob eine UnifiedPush-App installiert ist. */
    fun distributorAvailable(): Boolean

    /** Name der gewählten UnifiedPush-App. */
    fun currentDistributor(): String?

    /**
     * Lässt eine UnifiedPush-App wählen, falls noch keine gewählt ist.
     * Braucht eine Activity, weil dafür unter Umständen ein Auswahldialog
     * erscheint.
     */
    fun chooseDistributor(activity: Activity, onResult: (Boolean) -> Unit)

    suspend fun enable(instanceId: String)

    suspend fun disable(instanceId: String)

    /** Lässt das Plugin einen Testweckruf schicken; Ergebnis: angenommene Adressen. */
    suspend fun sendTest(instanceId: String): Outcome<Int>

    companion object {
        /** Für Tests: keine UnifiedPush-App vorhanden. */
        val NONE: PushSetup = object : PushSetup {
            override val errors = MutableStateFlow<Map<String, AppError>>(emptyMap())
            override fun distributorAvailable() = false
            override fun currentDistributor(): String? = null
            override fun chooseDistributor(activity: Activity, onResult: (Boolean) -> Unit) =
                onResult(false)
            override suspend fun enable(instanceId: String) = Unit
            override suspend fun disable(instanceId: String) = Unit
            override suspend fun sendTest(instanceId: String): Outcome<Int> =
                Outcome.Failure(AppError.Unknown(ErrorTexts.PUSH_NO_DISTRIBUTOR))
        }
    }
}
