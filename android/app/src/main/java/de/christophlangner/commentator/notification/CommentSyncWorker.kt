package de.christophlangner.commentator.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.christophlangner.commentator.core.AppLog
import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.data.account.InstanceStore
import de.christophlangner.commentator.domain.model.WordPressInstance
import de.christophlangner.commentator.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Prüft im Hintergrund auf neue Kommentare - für alle eingerichteten Blogs.
 *
 * Läuft auch, wenn die App geschlossen ist. WorkManager kümmert sich um
 * Batterieschonung und darum, dass ohne Verbindung gar nicht erst gestartet
 * wird.
 *
 * Ein Durchgang über alle Blogs statt eine Arbeit je Blog: Das Gerät wacht
 * einmal auf statt n-mal, und die Abfragen laufen ohnehin über dieselbe
 * Verbindung. Der Preis ist, dass die Fehlerbehandlung hier von Hand je Blog
 * geschieht - ein Blog, dessen Zugangsdaten abgelehnt werden, darf die
 * übrigen nicht mitnehmen.
 */
@HiltWorker
class CommentSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val instanceStore: InstanceStore,
    private val settingsRepository: SettingsRepository,
    private val newCommentSource: NewCommentSource,
    private val notifier: CommentNotifier,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val instances = instanceStore.all()
        if (instances.isEmpty()) return Result.success()
        if (!settingsRepository.settings.first().notificationsEnabled) return Result.success()

        // Steht mehr als ein Blog, nennt die Benachrichtigung ihn auch. Bei
        // nur einem wäre der Name überall dieselbe Zeile und damit nichts als
        // Rauschen.
        val nameNennen = instances.size > 1

        // Ein netzbedingter Fehler soll den ganzen Durchgang wiederholen
        // lassen, aber erst, nachdem alle übrigen Blogs abgearbeitet sind:
        // Ein früher Abbruch hieße, dass ein unerreichbarer Blog die
        // Benachrichtigungen aller anderen aufhält.
        var erneutVersuchen = false

        instances.forEach { instance ->
            val site = settingsRepository.siteSettings(instance.id).first()
            if (!site.notificationsEnabled) return@forEach
            if (pruefe(instance, nameNennen) == Ergebnis.ERNEUT) erneutVersuchen = true
        }

        return if (erneutVersuchen) Result.retry() else Result.success()
    }

    /** Wie es für einen einzelnen Blog ausgegangen ist. */
    private enum class Ergebnis { FERTIG, ERNEUT }

    private suspend fun pruefe(instance: WordPressInstance, nameNennen: Boolean): Ergebnis =
        when (val outcome = newCommentSource.fetchUnnotified(instance)) {
            is Outcome.Success -> {
                val gefunden = outcome.value

                if (!newCommentSource.hasBaseline(instance)) {
                    // Beim allerersten Lauf gibt es keinen Vergleichspunkt.
                    // Der Stand wird festgehalten, ohne zu melden - auch wenn
                    // gerade nichts offen ist, sonst gälte der nächste Lauf
                    // erneut als erster.
                    AppLog.d("Erster Lauf: Ausgangszustand wird gesetzt")
                    newCommentSource.markNotified(instance, gefunden.all)
                } else if (gefunden.all.isNotEmpty()) {
                    // Gemeldet wird nur, was nicht stummgeschaltet ist -
                    // vermerkt wird beides, sonst käme das Stumme bei jedem
                    // Lauf erneut vom Blog.
                    if (gefunden.toReport.isNotEmpty()) {
                        notifier.notifyNewComments(instance, gefunden.toReport, nameNennen)
                    }
                    newCommentSource.markNotified(instance, gefunden.all)
                }

                // Je Blog: Der Hinweis dieses Blogs verschwindet, wenn er
                // wieder antwortet. Ein gemeinsamer Hinweis würde von jedem
                // anderen erreichbaren Blog weggeräumt.
                notifier.clearSessionInvalid(instance)
                Ergebnis.FERTIG
            }

            is Outcome.Failure -> when (outcome.error) {
                AppError.Unauthorized -> {
                    notifier.notifySessionInvalid(instance)
                    // Erneute Versuche helfen nicht, solange die Zugangsdaten
                    // ungültig sind.
                    Ergebnis.FERTIG
                }

                AppError.NoConnection, AppError.Timeout, is AppError.ServerError,
                is AppError.RateLimited,
                -> Ergebnis.ERNEUT

                else -> Ergebnis.FERTIG
            }
        }

    companion object {
        const val UNIQUE_NAME = "commentator-comment-sync"
    }
}
