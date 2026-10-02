package de.christophlangner.commentator.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrl
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
        DemoHostInterceptor,
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
            redactHeader("Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
        },
    )
}

/**
 * Nur Debug: leitet einen Blog auf die lokale Testumgebung um - für
 * Screenshots und Videos, die eine echte Adresse zeigen sollen, ohne den
 * echten Blog zu berühren.
 *
 * Aus, solange nichts gesetzt ist. Eingeschaltet über eine Systemeigenschaft,
 * die nur die Shell setzen darf:
 *
 * ```
 * adb shell setprop debug.commentator.demo linuxundich.de=localhost:8443
 * ```
 *
 * Anfragen an den Blog (mit oder ohne www) gehen dann an die Testumgebung;
 * die Kopfzeile Host bleibt der Blog, damit WordPress dort seine Adresse
 * wiedererkennt. Bilder lädt Coil über einen eigenen Client, also weiter vom
 * echten Blog.
 */
private object DemoHostInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val (blog, ziel) = mapping() ?: return chain.proceed(request)
        val host = request.url.host
        if (host != blog && host != "www.$blog") return chain.proceed(request)

        val umgeleitet = ("https://$ziel").toHttpUrl()
        val url = request.url.newBuilder()
            .host(umgeleitet.host)
            .port(umgeleitet.port)
            .build()
        return chain.proceed(
            request.newBuilder()
                .url(url)
                .header("Host", host)
                .build(),
        )
    }

    private fun mapping(): Pair<String, String>? {
        val wert = runCatching {
            Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java)
                .invoke(null, "debug.commentator.demo") as String
        }.getOrNull().orEmpty()
        val teile = wert.split('=', limit = 2)
        return if (teile.size == 2 && teile.all { it.isNotBlank() }) teile[0] to teile[1] else null
    }
}

