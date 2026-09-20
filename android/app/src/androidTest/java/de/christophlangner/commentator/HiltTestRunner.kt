package de.christophlangner.commentator

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Ersetzt die Anwendungsklasse in Instrumentierungstests.
 *
 * Nötig, weil [CommentatorApplication] mit `@HiltAndroidApp` annotiert ist und
 * beim Start WorkManager konfiguriert - im Test soll stattdessen Hilts
 * Testanwendung laufen.
 */
class HiltTestRunner : AndroidJUnitRunner() {

    override fun newApplication(
        classLoader: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(classLoader, HiltTestApplication::class.java.name, context)
}
