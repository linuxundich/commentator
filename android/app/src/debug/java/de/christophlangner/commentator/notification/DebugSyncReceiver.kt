package de.christophlangner.commentator.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Stößt die Hintergrundprüfung einmal sofort an - nur im Debug-Build.
 *
 * WorkManager zieht periodische Arbeit nicht vor, auch nicht über
 * `cmd jobscheduler run -f`. Für Prüfungen am Gerät:
 *
 * ```
 * adb shell am broadcast -n de.christophlangner.commentator.debug/de.christophlangner.commentator.notification.DebugSyncReceiver
 * ```
 *
 * Geschützt über die Berechtigung DUMP, die nur die Shell besitzt.
 */
class DebugSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<CommentSyncWorker>().build(),
        )
    }
}
