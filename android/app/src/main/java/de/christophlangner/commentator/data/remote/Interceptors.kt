package de.christophlangner.commentator.data.remote

import de.christophlangner.commentator.data.account.CredentialSource
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Hängt die Basic-Auth-Kopfzeile mit dem Application Password an.
 *
 * Die Zugangsdaten werden von der [CredentialSource] im Speicher gehalten, der
 * [runBlocking]-Aufruf trifft deshalb nach dem ersten Mal nur noch den Cache.
 * OkHttp-Interceptors laufen ohnehin blockierend auf einem Hintergrund-Thread.
 */
class AuthInterceptor(
    private val instanceId: String,
    private val credentials: CredentialSource,
    private val sessionMonitor: SessionMonitor,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val stored = runBlocking { credentials.load(instanceId) }
        val request = if (stored == null) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", stored.toBasicAuthHeader())
                .build()
        }

        val response = chain.proceed(request)
        when (response.code) {
            401 -> sessionMonitor.reportUnauthorized(instanceId)
            in 200..299 -> sessionMonitor.reportAuthorized(instanceId)
        }
        return response
    }
}

/**
 * Lässt ausschließlich HTTPS zu.
 *
 * Die Netzwerkkonfiguration der App verbietet Klartextverkehr bereits auf
 * Plattformebene; diese zweite Schranke stellt sicher, dass auch ein
 * fehlerhaft zusammengesetzter Aufruf nicht unverschlüsselt hinausgeht.
 */
object HttpsOnlyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.isHttps) {
            throw IOException("Unverschlüsselte Verbindungen sind nicht zulässig")
        }
        return chain.proceed(request)
    }
}

/** Kennzeichnet die App gegenüber dem Server, ohne Geräte- oder Nutzerdaten preiszugeben. */
class UserAgentInterceptor(private val userAgent: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(
            chain.request().newBuilder()
                .header("User-Agent", userAgent)
                .build(),
        )
}
