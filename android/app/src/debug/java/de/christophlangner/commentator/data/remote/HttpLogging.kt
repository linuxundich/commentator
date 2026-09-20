package de.christophlangner.commentator.data.remote

import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Debug-Variante: ausführliches HTTP-Logging.
 *
 * Die Kopfzeile `Authorization` wird dabei ausdrücklich redigiert, damit das
 * Application Password auch beim Entwickeln nicht im Logcat steht. Die
 * Release-Variante dieser Datei liefert eine leere Liste, und die
 * Logging-Bibliothek ist dort gar nicht erst Teil der Anwendung.
 */
object HttpLogging {

    fun interceptors(): List<Interceptor> = listOf(
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
            redactHeader("Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
        },
    )
}
