package de.christophlangner.commentator.core

import android.util.Log
import de.christophlangner.commentator.BuildConfig

/**
 * Logging-Fassade. In Release-Builds werden alle Aufrufe zu No-Ops, damit
 * weder technische Details noch versehentlich übergebene Zugangsdaten auf
 * einem Produktivgerät im Logcat landen.
 */
object AppLog {

    private const val TAG = "Commentator"

    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.w(TAG, message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.e(TAG, message, throwable)
    }
}
