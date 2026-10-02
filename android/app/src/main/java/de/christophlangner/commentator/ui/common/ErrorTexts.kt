package de.christophlangner.commentator.ui.common

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import de.christophlangner.commentator.R
import de.christophlangner.commentator.core.error.AppError

/**
 * Übersetzt [AppError] in verständliche Texte.
 *
 * Nimmt [Resources] statt eines Context, weil `LocalResources` in Compose im
 * Gegensatz zu `LocalContext` auf Konfigurationsänderungen reagiert - sonst
 * stünde nach einem Sprachwechsel der alte Text in der Meldung.
 *
 * Die einzige Stelle, an der ein Fehler für Menschen formuliert wird. Es gibt
 * keine technischen Meldungen und keine Stacktraces in der Oberfläche - die
 * Details stehen ausschließlich im Debug-Log.
 */
object ErrorTexts {

    /** Kennungen für Fehler der Sofortmeldung, die keine eigene Fehlerart brauchen. */
    const val PUSH_NO_DISTRIBUTOR = "push_no_distributor"
    const val PUSH_REGISTRATION_FAILED = "push_registration_failed"
    const val PUSH_PLUGIN_OUTDATED = "push_plugin_outdated"
    const val PUSH_DISABLED_BY_SITE = "push_disabled_by_site"

    fun message(resources: Resources, error: AppError): String = when (error) {
        AppError.NoConnection -> resources.getString(R.string.error_no_connection)
        AppError.Timeout -> resources.getString(R.string.error_timeout)
        AppError.InsecureConnection -> resources.getString(R.string.error_insecure_connection)
        AppError.Unauthorized -> resources.getString(R.string.error_unauthorized)
        AppError.Forbidden -> resources.getString(R.string.error_forbidden)
        AppError.NotFound -> resources.getString(R.string.error_not_found)
        AppError.InvalidSite -> resources.getString(R.string.error_invalid_site)
        AppError.InsecureSiteUrl -> resources.getString(R.string.error_insecure_site_url)
        AppError.ApplicationPasswordsUnavailable ->
            resources.getString(R.string.error_app_passwords_unavailable)

        AppError.MalformedResponse -> resources.getString(R.string.error_malformed_response)
        AppError.OfflineWriteBlocked -> resources.getString(R.string.error_offline_write_blocked)

        is AppError.RateLimited -> error.retryAfterSeconds
            ?.let {
                resources.getQuantityString(
                    R.plurals.error_rate_limited_with_delay,
                    it.toInt(),
                    it,
                )
            }
            ?: resources.getString(R.string.error_rate_limited)

        is AppError.ServerError -> resources.getString(R.string.error_server, error.code)

        is AppError.WordPress -> when (error.code) {
            "empty_reply" -> resources.getString(R.string.error_empty_reply)
            // WordPress weist wortgleiche Kommentare ab. Das trifft einen
            // Moderator, der mehrfach dieselbe kurze Antwort schreibt.
            "comment_duplicate" -> resources.getString(R.string.error_comment_duplicate)
            // Und es bremst schnell aufeinanderfolgende Kommentare desselben
            // Autors aus - beim Abarbeiten einer Warteschlange leicht erreicht.
            "comment_flood" -> resources.getString(R.string.error_comment_flood)
            "rest_comment_invalid_id" -> resources.getString(R.string.error_comment_gone)
            "rest_comment_trash_disabled" -> resources.getString(R.string.error_trash_disabled)
            else -> resources.getString(R.string.error_wordpress, error.code)
        }

        is AppError.Unknown -> when (error.marker) {
            PUSH_NO_DISTRIBUTOR -> resources.getString(R.string.error_push_no_distributor)
            PUSH_REGISTRATION_FAILED -> resources.getString(R.string.error_push_registration_failed)
            PUSH_PLUGIN_OUTDATED -> resources.getString(R.string.error_push_plugin_outdated)
            PUSH_DISABLED_BY_SITE -> resources.getString(R.string.error_push_disabled_by_site)
            else -> resources.getString(R.string.error_unknown, error.marker)
        }
    }

    /** Ob der Fehler durch erneutes Anmelden behoben werden kann. */
    fun requiresReauthentication(error: AppError): Boolean = error == AppError.Unauthorized
}

@Composable
fun AppError.asMessage(): String = ErrorTexts.message(LocalResources.current, this)
