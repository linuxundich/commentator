package de.christophlangner.commentator

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
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
class CommentatorApplication :
    Application(),
    Configuration.Provider,
    SingletonImageLoader.Factory {

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

    /**
     * Bildzwischenspeicher im Arbeitsspeicher und auf der Platte.
     *
     * Ohne Plattenspeicher laedt Coil das Blog-Symbol und jeden Avatar nach
     * jedem Neustart erneut - sichtbar als kurzes Aufploppen und als
     * vermeidbarer Netzverkehr. Die Obergrenze haelt den Verbrauch in
     * Grenzen; mehr als ein paar Dutzend kleine Bilder kommen hier ohnehin
     * nicht zusammen.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, MEMORY_CACHE_SHARE)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(DISK_CACHE_BYTES)
                    .build()
            }
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

    private companion object {
        /** Anteil des verfuegbaren Speichers fuer Bilder. */
        const val MEMORY_CACHE_SHARE = 0.20

        const val DISK_CACHE_BYTES = 20L * 1024 * 1024
    }
}
