package de.christophlangner.commentator.ui.setup

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.christophlangner.commentator.R
import de.christophlangner.commentator.ui.common.ScreenshotProtection
import de.christophlangner.commentator.ui.common.asMessage

/**
 * Einrichtung einer WordPress-Verbindung.
 *
 * Der Regelweg führt über den Autorisierungs-Flow von WordPress: Das
 * Kontokennwort wird ausschließlich im Browser eingegeben, die App erhält nur
 * ein einzeln widerrufbares Application Password.
 */
@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Nur der Schritt mit manueller Eingabe zeigt ein Application Password im
    // Klartext. Adresseingabe und Browser-Weg enthalten nichts Schützenswertes
    // und bleiben deshalb ganz normal fotografierbar.
    ScreenshotProtection(enabled = state.step == SetupStep.ManualCredentials)

    LaunchedEffect(state.isDone) {
        if (state.isDone) onSetupComplete()
    }

    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.setup_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            when (state.step) {
                SetupStep.EnterUrl -> UrlStep(
                    value = state.siteUrlInput,
                    enabled = !state.isBusy,
                    onValueChange = viewModel::onUrlChanged,
                    onSubmit = viewModel::checkSite,
                    canSubmit = state.canSubmitUrl,
                )

                SetupStep.Authorize -> AuthorizeStep(
                    siteName = state.discovery?.siteName.orEmpty(),
                    isBusy = state.isBusy,
                    onAuthorize = {
                        val url = viewModel.authorizationUrl()
                        if (url != null) {
                            viewModel.onAuthorizationStarted()
                            CustomTabsIntent.Builder()
                                .setShowTitle(true)
                                .build()
                                .launchUrl(context, url.toUri())
                        }
                    },
                    onUseManual = viewModel::useManualCredentials,
                    onBack = viewModel::back,
                )

                SetupStep.ManualCredentials -> ManualStep(
                    username = state.username,
                    applicationPassword = state.applicationPassword,
                    enabled = !state.isBusy,
                    canSubmit = state.canSubmitCredentials,
                    onUsernameChange = viewModel::onUsernameChanged,
                    onPasswordChange = viewModel::onApplicationPasswordChanged,
                    onSubmit = viewModel::submitManualCredentials,
                    onBack = viewModel::back,
                )
            }

            if (state.isBusy) {
                CircularProgressIndicator()
            }

            state.error?.let { error ->
                Text(
                    text = error.asMessage(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun UrlStep(
    value: String,
    enabled: Boolean,
    canSubmit: Boolean,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Text(
        text = stringResource(R.string.setup_url_explanation),
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(stringResource(R.string.setup_url_label)) },
        placeholder = { Text("https://example.com") },
        supportingText = { Text(stringResource(R.string.setup_url_https_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = onSubmit, enabled = canSubmit, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_continue))
    }
}

@Composable
private fun AuthorizeStep(
    siteName: String,
    isBusy: Boolean,
    onAuthorize: () -> Unit,
    onUseManual: () -> Unit,
    onBack: () -> Unit,
) {
    Text(
        text = stringResource(R.string.setup_authorize_explanation, siteName),
        style = MaterialTheme.typography.bodyMedium,
    )
    Button(onClick = onAuthorize, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_authorize_in_browser))
    }
    TextButton(onClick = onUseManual, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_use_manual_credentials))
    }
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_back))
    }
}

@Composable
private fun ManualStep(
    username: String,
    applicationPassword: String,
    enabled: Boolean,
    canSubmit: Boolean,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    Text(
        text = stringResource(R.string.setup_manual_explanation),
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        enabled = enabled,
        label = { Text(stringResource(R.string.setup_username_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = applicationPassword,
        onValueChange = onPasswordChange,
        enabled = enabled,
        label = { Text(stringResource(R.string.setup_app_password_label)) },
        supportingText = { Text(stringResource(R.string.setup_app_password_hint)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = onSubmit, enabled = canSubmit, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_sign_in))
    }
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_back))
    }
}
