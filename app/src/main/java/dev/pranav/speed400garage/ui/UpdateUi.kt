package dev.pranav.speed400garage.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pranav.speed400garage.update.UpdateState

/**
 * The update prompt.
 *
 * It is a dialog only once there is genuinely something to act on. A check that finds
 * nothing, or that has not been configured yet, says nothing at all — an app that
 * interrupts every launch to report "you are up to date" trains you to dismiss it
 * without reading, which is exactly when you will dismiss the one that mattered.
 */
@Composable
fun UpdatePrompt(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    when (val s = state) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("Version ${s.manifest.versionName} is available") },
            text = {
                Column {
                    Text("You're on ${viewModel.currentVersion}.")
                    if (s.manifest.notes.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(s.manifest.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Downloads ${"%.1f".format(s.asset.sizeBytes / 1_048_576.0)} MB, then Android " +
                            "will ask you to confirm the install. Your logged data is untouched.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { Button(onClick = viewModel::download) { Text("Download") } },
            dismissButton = { TextButton(onClick = viewModel::dismiss) { Text("Later") } },
        )

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Downloading ${s.manifest.versionName}") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { s.fraction }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("${(s.fraction * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = viewModel::dismiss) { Text("Cancel") } },
        )

        is UpdateState.ReadyToInstall -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("Ready to install ${s.manifest.versionName}") },
            text = {
                Text(
                    if (viewModel.canInstall()) {
                        "Checksum verified. Android will ask you to confirm."
                    } else {
                        "Checksum verified. Android needs permission to install apps from " +
                            "Speed 400 Garage first — this is a one-time grant."
                    }
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (viewModel.canInstall()) {
                        context.launch(viewModel.installIntent(s.path))
                        viewModel.dismiss()
                    } else {
                        context.launch(viewModel.permissionIntent())
                    }
                }) { Text(if (viewModel.canInstall()) "Install" else "Grant permission") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismiss) { Text("Later") } },
        )

        is UpdateState.Failed -> if (!s.needsToken) AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("Update check failed") },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = viewModel::dismiss) { Text("OK") } },
        )

        UpdateState.Checking, UpdateState.Idle, UpdateState.UpToDate -> Unit
    }
}

private fun Context.launch(intent: Intent) = runCatching { startActivity(intent) }

/** The Settings pane's update section — token, toggle, and a manual check. */
@Composable
fun UpdateSettingsCard(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hasToken by viewModel.hasToken.collectAsStateWithLifecycle()
    val autoCheck by viewModel.autoCheck.collectAsStateWithLifecycle()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Updates", style = MaterialTheme.typography.titleLarge)
            Text("Installed: ${viewModel.currentVersion}", style = MaterialTheme.typography.bodyMedium)

            Text(
                "The repository is private, so checking for a new build needs a GitHub token " +
                    "with read access to it. The token is stored encrypted on this tablet and is " +
                    "the only thing sent — no data about the bike ever leaves the device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            GitHubTokenField(hasToken = hasToken, onSave = viewModel::setToken)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = viewModel::check, enabled = hasToken && state !is UpdateState.Checking) {
                    Text(if (state is UpdateState.Checking) "Checking…" else "Check now")
                }
                TextButton(onClick = { viewModel.setAutoCheck(!autoCheck) }) {
                    Text(if (autoCheck) "Auto-check on: turn off" else "Auto-check off: turn on")
                }
            }

            when (val s = state) {
                UpdateState.UpToDate -> Text("Up to date.", style = MaterialTheme.typography.bodyMedium)
                is UpdateState.Failed -> Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                else -> Unit
            }
        }
    }
}
