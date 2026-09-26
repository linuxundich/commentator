package de.christophlangner.commentator.ui.navigation

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import de.christophlangner.commentator.ui.about.AboutScreen
import de.christophlangner.commentator.ui.common.LoadingState
import de.christophlangner.commentator.ui.detail.CommentDetailScreen
import de.christophlangner.commentator.ui.inbox.InboxScreen
import de.christophlangner.commentator.ui.settings.SettingsScreen
import de.christophlangner.commentator.ui.settings.SiteSettingsScreen
import de.christophlangner.commentator.ui.setup.SetupScreen

/**
 * Navigationsgerüst der App.
 *
 * Eine einzige Activity, typsichere Routen, ein Deep Link. Die
 * Benachrichtigung benutzt genau dieselbe Route wie die Liste - es gibt keinen
 * zweiten Weg in die Detailansicht.
 */
@Composable
fun CommentatorApp(
    deepLinkIntent: Intent?,
    onDeepLinkHandled: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RootViewModel = hiltViewModel(),
) {
    val hasInstance by viewModel.hasInstance.collectAsStateWithLifecycle()

    Surface(modifier = modifier.fillMaxSize()) {
        when (hasInstance) {
            null -> LoadingState()
            else -> {
                val navController = rememberNavController()
                val startDestination: Any = remember {
                    if (hasInstance == true) InboxRoute else SetupRoute
                }

                LaunchedEffect(deepLinkIntent) {
                    val intent = deepLinkIntent ?: return@LaunchedEffect
                    // Zuerst der Blog, dann das Ziel: Die Detailansicht trägt
                    // die Kennung selbst, der Posteingang hinter ihr nicht.
                    DeepLinks.instanceIdOf(intent.data)?.let(viewModel::setActiveInstance)
                    // Der Posteingang ist kein eigenes Ziel - für ihn genügt
                    // der Wechsel, die App steht schon dort.
                    if (DeepLinks.isComment(intent.data)) navController.handleDeepLink(intent)
                    onDeepLinkHandled()
                }

                NavHost(navController = navController, startDestination = startDestination) {
                    composable<SetupRoute> {
                        SetupScreen(
                            onSetupComplete = {
                                // Beim Hinzufügen eines weiteren Blogs liegt
                                // der Posteingang schon im Rücken; ein
                                // zweiter daneben wäre eine Sackgasse, aus
                                // der „zurück" in die Einrichtung führt.
                                val zurueck = navController.popBackStack(InboxRoute, false)
                                if (!zurueck) {
                                    navController.navigate(InboxRoute) {
                                        popUpTo<SetupRoute> { inclusive = true }
                                    }
                                }
                            },
                            onNavigateUp = { navController.navigateUp() },
                            // Ohne eingerichteten Blog gibt es keinen Weg
                            // zurück - dahinter liegt nichts.
                            canNavigateUp = hasInstance == true,
                        )
                    }

                    composable<InboxRoute> {
                        InboxScreen(
                            onOpenComment = { comment ->
                                navController.navigate(
                                    CommentDetailRoute(comment.instanceId, comment.id),
                                )
                            },
                            onOpenSettings = { navController.navigate(SettingsRoute) },
                            onAddSite = { navController.navigate(SetupRoute) },
                            onReauthenticate = { navController.navigate(SetupRoute) },
                        )
                    }

                    composable<CommentDetailRoute>(
                        deepLinks = listOf(
                            navDeepLink<CommentDetailRoute>(
                                basePath = DeepLinks.COMMENT_BASE_PATH,
                            ),
                        ),
                    ) {
                        CommentDetailScreen(onNavigateUp = { navController.navigateUp() })
                    }

                    composable<AboutRoute> {
                        AboutScreen(onNavigateUp = { navController.navigateUp() })
                    }

                    composable<SettingsRoute> {
                        SettingsScreen(
                            onNavigateUp = { navController.navigateUp() },
                            onOpenAbout = { navController.navigate(AboutRoute) },
                            onOpenSite = { instanceId ->
                                navController.navigate(SiteSettingsRoute(instanceId))
                            },
                            onAddSite = { navController.navigate(SetupRoute) },
                        )
                    }

                    // Welcher Blog gemeint ist, liest das ViewModel selbst
                    // aus der Route - so bleibt die Kennung an einer Stelle.
                    composable<SiteSettingsRoute> {
                        SiteSettingsScreen(
                            onNavigateUp = { navController.navigateUp() },
                            // Nach dem Entfernen des letzten Blogs gibt es
                            // keinen Posteingang mehr, den man zeigen könnte.
                            onLastSiteRemoved = {
                                navController.navigate(SetupRoute) {
                                    popUpTo(navController.graph.id) { inclusive = true }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
