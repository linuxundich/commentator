package de.christophlangner.commentator.ui.about

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import de.christophlangner.commentator.BuildConfig
import de.christophlangner.commentator.R
import de.christophlangner.commentator.ui.common.rememberLabelWidth

/**
 * Angaben zur App: Version, Herkunft, Rechtliches.
 *
 * Die Versionsangabe ist bewusst vollständig und nicht geschönt. Wer einen
 * Fehler meldet, soll den genauen Stand nennen können, aus dem der Build
 * entstanden ist - dafür steht der Commit mit dabei.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        AboutContent(
            onOpenRepository = {
                context.startActivity(Intent(Intent.ACTION_VIEW, REPOSITORY_URL.toUri()))
            },
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/** Ohne Zugriff auf das System, damit der Inhalt ohne Gerät prüfbar ist. */
@Composable
internal fun AboutContent(
    onOpenRepository: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = stringResource(R.string.about_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        HorizontalDivider()

        val stil = MaterialTheme.typography.bodyMedium
        val beschriftungen = listOf(
            stringResource(R.string.about_version),
            stringResource(R.string.about_build),
            stringResource(R.string.about_commit),
        )
        val spaltenbreite = rememberLabelWidth(beschriftungen, stil)

        AboutRow(beschriftungen[0], BuildConfig.VERSION_NAME, spaltenbreite, stil)
        AboutRow(beschriftungen[1], BuildConfig.VERSION_CODE.toString(), spaltenbreite, stil)
        AboutRow(
            label = beschriftungen[2],
            value = BuildConfig.GIT_COMMIT + if (BuildConfig.GIT_DIRTY) {
                " " + stringResource(R.string.about_commit_dirty)
            } else {
                ""
            },
            labelWidth = spaltenbreite,
            style = stil,
        )

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        Text(
            text = stringResource(R.string.about_privacy_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = stringResource(R.string.about_privacy_body),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )

        Text(
            text = stringResource(R.string.about_license_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = stringResource(R.string.about_license_body),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )

        TextButton(
            onClick = onOpenRepository,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        ) {
            Text(stringResource(R.string.about_open_repository))
        }
    }
}

@Composable
private fun AboutRow(
    label: String,
    value: String,
    labelWidth: Dp,
    style: TextStyle,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            // Beschriftung und Wert gehoeren zusammen und werden zusammen
            // vorgelesen.
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = label,
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(labelWidth),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = style,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val REPOSITORY_URL = "https://github.com/linuxundich/commentator"
