package de.christophlangner.commentator.core.error

import de.christophlangner.commentator.core.AppLog
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Übersetzt Ausnahmen und HTTP-Antworten in [AppError].
 *
 * Bewusst die einzige Stelle, an der Exceptions ausgewertet werden. Der
 * Stacktrace geht ausschließlich ins Debug-Log, nie in den Rückgabewert.
 */
object ErrorMapper {

    fun fromThrowable(throwable: Throwable): AppError {
        AppLog.w("Fehler wird abgebildet: ${throwable::class.simpleName}", throwable)
        return when (throwable) {
            is SocketTimeoutException -> AppError.Timeout
            is UnknownHostException, is ConnectException -> AppError.NoConnection
            is SSLException -> AppError.InsecureConnection
            is SerializationException -> AppError.MalformedResponse
            is IOException -> AppError.NoConnection
            else -> AppError.Unknown(throwable::class.simpleName ?: "unbekannt")
        }
    }

    /**
     * @param wordPressCode der `code` aus dem WordPress-Fehlerkörper, sofern lesbar
     * @param retryAfter Wert des `Retry-After`-Headers in Sekunden
     */
    fun fromHttpStatus(
        status: Int,
        wordPressCode: String? = null,
        retryAfter: Long? = null,
    ): AppError = when {
        status == 401 -> AppError.Unauthorized
        status == 403 -> AppError.Forbidden
        status == 404 && wordPressCode == null -> AppError.NotFound
        status == 429 -> AppError.RateLimited(retryAfter)
        status >= 500 -> AppError.ServerError(status)
        wordPressCode != null -> AppError.WordPress(wordPressCode, status)
        status == 404 -> AppError.NotFound
        else -> AppError.Unknown("http_$status")
    }
}
