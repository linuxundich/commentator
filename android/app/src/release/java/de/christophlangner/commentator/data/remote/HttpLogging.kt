package de.christophlangner.commentator.data.remote

import okhttp3.Interceptor

/**
 * Release-Variante: kein HTTP-Logging.
 *
 * Die Logging-Bibliothek ist in Release-Builds nicht enthalten, es kann also
 * auch versehentlich nichts protokolliert werden.
 */
object HttpLogging {
    fun interceptors(): List<Interceptor> = emptyList()
}
