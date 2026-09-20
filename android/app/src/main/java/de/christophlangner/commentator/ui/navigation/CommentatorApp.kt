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
import de.christophlangner.commentator.ui.common.LoadingState
import de.christophlangner.commentator.ui.detail.CommentDetailScreen
import de.christophlangner.commentator.ui.inbox.InboxScreen
import de.christophlangner.commentator.ui.settings.SettingsScreen
import de.christophlangner.commentator.ui.setup.SetupScreen

/**
 * Navigationsgerüst der App.
 *
 * Eine einzige Activity, typsichere Routen, ein Deep Link. Die
 * Benachrichtigung benutzt genau dieselbe Route wie die Liste - es gibt keinen
 * zweiten Weg in die Detailansicht, der auseinanderlaufen könnte.
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
                    navController.handleDeepLink(intent)
                    onDeepLinkHandled()
                }

                NavHost(navController = navController, startDestination = startDestination) {
                    composable<SetupRoute> {
                        SetupScreen(
                            onSetupComplete = {
                                navController.navigate(InboxRoute) {
                                    popUpTo<SetupRoute> { inclusive = true }
                                }
                            },
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

                    composable<SettingsRoute> {
                        SettingsScreen(
                            onNavigateUp = { navController.navigateUp() },
                            onSignedOut = {
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
