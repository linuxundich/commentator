package de.christophlangner.commentator.push

import android.app.Activity
import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import de.christophlangner.commentator.core.AppLog
import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.data.account.InstanceStore
import de.christophlangner.commentator.domain.repository.CommentRepository
import de.christophlangner.commentator.domain.repository.SettingsRepository
import de.christophlangner.commentator.notification.CommentNotifier
import de.christophlangner.commentator.notification.CommentSyncWorker
import de.christophlangner.commentator.ui.common.ErrorTexts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import org.unifiedpush.android.connector.UnifiedPush
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sofortmeldung über UnifiedPush - die Ergänzung zur regelmäßigen Prüfung.
 *
 * Der Ablauf: Die App meldet sich je Blog bei einer UnifiedPush-App auf dem
 * Telefon an (etwa ntfy). Die antwortet mit einer Adresse, die die App beim
 * Plugin hinterlegt. Geht ein Kommentar ein, ruft das Plugin diese Adresse
 * auf, und die App stößt die gewohnte Prüfung sofort an.
 *
 * Über den Push-Weg geht kein Inhalt: Der Weckruf sagt nur „sieh nach". Die
 * Kommentare holt die App wie immer selbst. Deshalb muss die Nachricht auch
 * nicht verschlüsselt sein, und das Plugin braucht keine Kryptografie.
 *
 * Die Kennung der UnifiedPush-Registrierung ist die des Blogs. So hat jeder
 * Blog seine eigene Adresse, und eine Nachricht weiß, wem sie gilt.
 */
@Singleton
class InstantPush @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val repository: CommentRepository,
    private val instanceStore: InstanceStore,
    private val notifier: CommentNotifier,
) : PushSetup {

    private val _errors = MutableStateFlow<Map<String, AppError>>(emptyMap())

    /** Der letzte Fehler je Blog, bis zum nächsten Versuch. */
    override val errors: StateFlow<Map<String, AppError>> = _errors.asStateFlow()

    /** Ob überhaupt eine UnifiedPush-App installiert ist. */
    override fun distributorAvailable(): Boolean = UnifiedPush.getDistributors(context).isNotEmpty()

    /** Name der gewählten UnifiedPush-App, sofern eine gewählt ist. */
    override fun currentDistributor(): String? = UnifiedPush.getAckDistributor(context)?.let { paket ->
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(paket, 0)).toString()
        }.getOrDefault(paket)
    }

    /**
     * Meldet den Blog an, nachdem die Oberfläche eine UnifiedPush-App
     * gewählt hat. Die Adresse kommt später über [onNewEndpoint].
     */
    override fun chooseDistributor(activity: Activity, onResult: (Boolean) -> Unit) =
        UnifiedPush.tryUseCurrentOrDefaultDistributor(activity, onResult)

    override suspend fun enable(instanceId: String) {
        val instance = instanceStore.byId(instanceId) ?: return
        clearError(instanceId)
        settings.setInstantPush(instanceId, true)
        UnifiedPush.register(context, instanceId, messageForDistributor = instance.displayName)
    }

    /** Meldet den Blog ab - beim Verteiler und beim Plugin. */
    override suspend fun disable(instanceId: String) {
        settings.setInstantPush(instanceId, false)
        clearError(instanceId)
        UnifiedPush.unregister(context, instanceId)
        forgetEndpoint(instanceId)
    }

    /**
     * Die UnifiedPush-App hat eine (neue) Adresse vergeben.
     *
     * Erst wenn das Plugin sie angenommen hat, gilt sie als hinterlegt. Eine
     * vorherige Adresse wird danach zurückgenommen, damit keine Weckrufe an
     * zwei Stellen gehen.
     */
    suspend fun onNewEndpoint(instanceId: String, endpoint: String) {
        val site = settings.siteSettings(instanceId).first()
        if (!site.instantPush) return
        if (site.pushEndpoint == endpoint) return

        when (val outcome = repository.registerPush(instanceId, endpoint)) {
            is Outcome.Success -> {
                settings.setPushEndpoint(instanceId, endpoint)
                site.pushEndpoint?.let { repository.unregisterPush(instanceId, it) }
                clearError(instanceId)
            }

            is Outcome.Failure -> {
                AppLog.d("Push-Adresse nicht hinterlegt: ${outcome.error}")
                val error = when (outcome.error) {
                    // Ein Plugin vor 1.5 kennt die Route nicht.
                    AppError.NotFound -> AppError.Unknown(ErrorTexts.PUSH_PLUGIN_OUTDATED)
                    // Der Admin hat sie mit COMMENTATOR_BRIDGE_DISABLE_PUSH abgeschaltet.
                    AppError.Forbidden -> AppError.Unknown(ErrorTexts.PUSH_DISABLED_BY_SITE)
                    else -> outcome.error
                }
                _errors.update { it + (instanceId to error) }
            }
        }
    }

    fun onRegistrationFailed(instanceId: String) {
        _errors.update { it + (instanceId to AppError.Unknown(ErrorTexts.PUSH_REGISTRATION_FAILED)) }
    }

    /** Die UnifiedPush-App hat die Registrierung von sich aus beendet. */
    suspend fun onUnregistered(instanceId: String) {
        settings.setInstantPush(instanceId, false)
        forgetEndpoint(instanceId)
    }

    /**
     * Ein Weckruf: Die gewohnte Prüfung läuft sofort.
     *
     * Für alle Blogs, nicht nur den gemeldeten - es ist derselbe Durchgang,
     * und das Gerät ist ohnehin wach. Liegt schon einer an, genügt der.
     */
    override suspend fun sendTest(instanceId: String): Outcome<Int> =
        when (val outcome = repository.testPush(instanceId)) {
            // Ein Plugin vor 1.7 kennt den Test nicht.
            is Outcome.Failure -> if (outcome.error == AppError.NotFound) {
                Outcome.Failure(AppError.Unknown(ErrorTexts.PUSH_PLUGIN_OUTDATED))
            } else {
                outcome
            }
            is Outcome.Success -> outcome
        }

    /**
     * Ein Weckruf ist eingetroffen.
     *
     * Der Inhalt zählt nur in einem Fall: „test" kommt vom Testknopf und
     * bestätigt mit einer eigenen Meldung, dass der Weg funktioniert. Alles
     * andere heißt „sieh nach".
     */
    suspend fun onMessage(instanceId: String, content: ByteArray) {
        if (content.decodeToString().trim() == TEST_MESSAGE) {
            instanceStore.byId(instanceId)?.let { notifier.notifyPushTest(it) }
            return
        }
        onMessage()
    }

    fun onMessage() {
        val request = OneTimeWorkRequestBuilder<CommentSyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(PUSH_SYNC_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private suspend fun forgetEndpoint(instanceId: String) {
        val endpoint = settings.siteSettings(instanceId).first().pushEndpoint ?: return
        // Geht das Zurücknehmen beim Plugin schief, bleibt dort eine tote
        // Adresse. Das Plugin hält je Konto nur wenige vor und verdrängt die
        // älteste; eine Wiederholung lohnt deshalb nicht.
        repository.unregisterPush(instanceId, endpoint)
        settings.setPushEndpoint(instanceId, null)
    }

    private fun clearError(instanceId: String) = _errors.update { it - instanceId }

    private companion object {
        const val PUSH_SYNC_NAME = "commentator-push-sync"
        const val TEST_MESSAGE = "test"
    }
}
