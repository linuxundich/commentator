package de.christophlangner.commentator

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toUri
import dagger.hilt.android.AndroidEntryPoint
import de.christophlangner.commentator.ui.navigation.CommentatorApp
import de.christophlangner.commentator.ui.navigation.DeepLinks
import de.christophlangner.commentator.ui.setup.AuthCallbackChannel
import de.christophlangner.commentator.ui.theme.CommentatorTheme
import javax.inject.Inject

/**
 * Einziger Einstiegspunkt der App.
 *
 * Es gibt bewusst nur eine Activity: Navigation, Zustand und Deep Links
 * laufen vollständig über Compose Navigation.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authCallbackChannel: AuthCallbackChannel

    /** Deep Link aus einer Benachrichtigung, bis die Navigation ihn übernommen hat. */
    private val pendingDeepLink = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        handleAuthCallback(intent)

        setContent {
            CommentatorTheme {
                CommentatorApp(
                    deepLinkIntent = pendingDeepLink.value,
                    onDeepLinkHandled = { pendingDeepLink.value = null },
                )
            }
        }

        if (DeepLinks.isScreenLink(intent.data)) pendingDeepLink.value = intent
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthCallback(intent)
        if (DeepLinks.isScreenLink(intent.data)) pendingDeepLink.value = intent
    }

    /**
     * Nimmt das Ergebnis des WordPress-Autorisierungs-Flows entgegen.
     *
     * Die Parameter enthalten das Application Password. Es wird sofort aus
     * dem Intent gelesen, an den Kanal übergeben und nirgends protokolliert.
     */
    private fun handleAuthCallback(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != DeepLinks.SCHEME) return

        when (data.host) {
            DeepLinks.AUTH_CALLBACK_HOST -> {
                val siteUrl = data.getQueryParameter("site_url")
                val userLogin = data.getQueryParameter("user_login")
                val password = data.getQueryParameter("password")

                if (siteUrl != null && userLogin != null && password != null) {
                    authCallbackChannel.submit(
                        AuthCallbackChannel.Callback(siteUrl, userLogin, password),
                    )
                } else {
                    authCallbackChannel.submitRejection()
                }
                // Der Intent darf nicht erneut ausgewertet werden.
                intent.data = "${DeepLinks.SCHEME}://consumed".toUri()
            }

            DeepLinks.AUTH_REJECTED_HOST -> {
                authCallbackChannel.submitRejection()
                intent.data = "${DeepLinks.SCHEME}://consumed".toUri()
            }
        }
    }
}
