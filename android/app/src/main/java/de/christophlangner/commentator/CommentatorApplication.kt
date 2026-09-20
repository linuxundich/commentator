package de.christophlangner.commentator

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import de.christophlangner.commentator.domain.repository.SettingsRepository
import de.christophlangner.commentator.notification.NotificationChannels
import de.christophlangner.commentator.notification.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class CommentatorApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var notificationChannels: NotificationChannels

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var syncScheduler: SyncScheduler

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * WorkManager wird nicht automatisch initialisiert, sondern hier - nur so
     * kann der Worker seine Abhängigkeiten über Hilt beziehen. Der
     * Standard-Initialisierer ist im Manifest abgeschaltet.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        notificationChannels.ensureCreated()
        observeSyncSettings()
    }

    /** Hält die geplante Hintergrundprüfung mit den Einstellungen im Gleichklang. */
    private fun observeSyncSettings() {
        applicationScope.launch {
            settingsRepository.settings
                .distinctUntilChanged { old, new ->
                    old.notificationsEnabled == new.notificationsEnabled &&
                        old.syncIntervalMinutes == new.syncIntervalMinutes
                }
                .collect { settings ->
                    if (settings.notificationsEnabled) {
                        syncScheduler.schedule(settings.syncIntervalMinutes)
                    } else {
                        syncScheduler.cancel()
                    }
                }
        }
    }
}
