package de.christophlangner.commentator.notification

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plant die Hintergrundprüfung.
 *
 * Bewusst über WorkManager statt über AlarmManager oder einen Vordergrunddienst:
 * Die Prüfung ist verschiebbar, und WorkManager hält sich von sich aus an die
 * Einschränkungen des Systems.
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun schedule(intervalMinutes: Int) {
        val request = PeriodicWorkRequestBuilder<CommentSyncWorker>(
            intervalMinutes.toLong().coerceAtLeast(MIN_INTERVAL_MINUTES),
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CommentSyncWorker.UNIQUE_NAME,
            // Eine Änderung des Intervalls soll sofort greifen, ein Neustart
            // der App dagegen nichts zurücksetzen.
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(CommentSyncWorker.UNIQUE_NAME)
    }

    private companion object {
        const val MIN_INTERVAL_MINUTES = 15L
    }
}
