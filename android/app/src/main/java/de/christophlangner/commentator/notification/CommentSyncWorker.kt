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
import de.christophlangner.commentator.data.local.dao.CommentDao
import de.christophlangner.commentator.data.local.entity.SyncStateEntity
import de.christophlangner.commentator.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Prüft im Hintergrund auf neue Kommentare.
 *
 * Läuft auch, wenn die App geschlossen ist. WorkManager kümmert sich um
 * Batterieschonung und darum, dass ohne Verbindung gar nicht erst gestartet
 * wird.
 */
@HiltWorker
class CommentSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val instanceStore: InstanceStore,
    private val settingsRepository: SettingsRepository,
    private val newCommentSource: NewCommentSource,
    private val notifier: CommentNotifier,
    private val dao: CommentDao,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val instance = instanceStore.currentActive() ?: return Result.success()
        if (!settingsRepository.settings.first().notificationsEnabled) return Result.success()

        return when (val outcome = newCommentSource.fetchUnnotified(instance)) {
            is Outcome.Success -> {
                val comments = outcome.value
                if (comments.isNotEmpty()) {
                    if (isFirstRun(instance.id)) {
                        // Beim allerersten Lauf gibt es keinen sinnvollen
                        // Vergleichspunkt. Statt den gesamten Rückstand zu
                        // melden, wird nur der Ausgangszustand festgehalten.
                        AppLog.d("Erster Lauf: Ausgangszustand wird gesetzt")
                        newCommentSource.markNotified(instance, comments)
                    } else {
                        notifier.notifyNewComments(instance, comments)
                        newCommentSource.markNotified(instance, comments)
                    }
                }
                notifier.clearSessionInvalid()
                Result.success()
            }

            is Outcome.Failure -> when (outcome.error) {
                AppError.Unauthorized -> {
                    notifier.notifySessionInvalid(instance)
                    // Erneute Versuche helfen nicht, solange die Zugangsdaten
                    // ungültig sind.
                    Result.success()
                }

                AppError.NoConnection, AppError.Timeout, is AppError.ServerError,
                is AppError.RateLimited,
                -> Result.retry()

                else -> Result.success()
            }
        }
    }

    private suspend fun isFirstRun(instanceId: String): Boolean {
        val state = dao.syncState(instanceId)
        if (state != null && state.lastNotifiedCommentId > 0L) return false

        if (state == null) {
            dao.upsertSyncState(
                SyncStateEntity(
                    instanceId = instanceId,
                    lastSyncEpochMillis = null,
                    lastNotifiedCommentId = 0,
                    lastNotifiedDateEpochMillis = 0,
                ),
            )
        }
        return true
    }

    companion object {
        const val UNIQUE_NAME = "commentator-comment-sync"
    }
}
