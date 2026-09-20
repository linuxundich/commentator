package de.christophlangner.commentator.data.remote

import de.christophlangner.commentator.core.Outcome
import de.christophlangner.commentator.core.error.AppError
import de.christophlangner.commentator.core.error.ErrorMapper
import de.christophlangner.commentator.data.remote.dto.WpErrorDto
import kotlinx.serialization.json.Json
import okhttp3.Headers
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/** Antwortkörper zusammen mit den Headern, die für Paginierung gebraucht werden. */
data class ApiResponse<T>(
    val body: T,
    val headers: Headers,
) {
    val totalPages: Int get() = headers["X-WP-TotalPages"]?.toIntOrNull() ?: 1
    val totalItems: Int get() = headers["X-WP-Total"]?.toIntOrNull() ?: 0
}

/**
 * Führt API-Aufrufe aus und übersetzt jedes denkbare Ergebnis in [Outcome].
 *
 * Damit gibt es genau eine Stelle, an der HTTP-Codes, Fehlerkörper und
 * Ausnahmen ausgewertet werden; alles darüber arbeitet nur noch mit
 * [AppError].
 */
@Singleton
class ApiExecutor @Inject constructor(
    private val json: Json,
) {

    suspend fun <T : Any> call(block: suspend () -> Response<T>): Outcome<ApiResponse<T>> =
        try {
            val response = block()
            if (response.isSuccessful) {
                val body = response.body()
                if (body == null) {
                    Outcome.Failure(AppError.MalformedResponse)
                } else {
                    Outcome.Success(ApiResponse(body, response.headers()))
                }
            } else {
                Outcome.Failure(toError(response))
            }
        } catch (throwable: Throwable) {
            if (throwable is kotlinx.coroutines.CancellationException) throw throwable
            Outcome.Failure(ErrorMapper.fromThrowable(throwable))
        }

    /** Für Aufrufe, deren Körper nicht interessiert (etwa DELETE). */
    suspend fun <T : Any> callIgnoringBody(block: suspend () -> Response<T>): Outcome<Unit> =
        try {
            val response = block()
            if (response.isSuccessful) {
                Outcome.Success(Unit)
            } else {
                Outcome.Failure(toError(response))
            }
        } catch (throwable: Throwable) {
            if (throwable is kotlinx.coroutines.CancellationException) throw throwable
            Outcome.Failure(ErrorMapper.fromThrowable(throwable))
        }

    private fun <T> toError(response: Response<T>): AppError {
        val wordPressCode = runCatching {
            response.errorBody()?.string()?.takeIf { it.isNotBlank() }?.let { raw ->
                json.decodeFromString<WpErrorDto>(raw).code.takeIf { it.isNotBlank() }
            }
        }.getOrNull()

        val retryAfter = response.headers()["Retry-After"]?.toLongOrNull()
        return ErrorMapper.fromHttpStatus(
            status = response.code(),
            wordPressCode = wordPressCode,
            retryAfter = retryAfter,
        )
    }
}
