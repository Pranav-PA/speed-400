package dev.pranav.speed400garage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding

@Composable
fun SettingsScreen() {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.displaySmall)
        dev.pranav.speed400garage.ui.assistant.HandbookCard()
        dev.pranav.speed400garage.ui.log.BackupCard()
        UpdateSettingsCard()

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What this app sends", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Nothing about the motorcycle. The update check above is the only outbound " +
                        "request the app makes, and it carries a GitHub token and nothing else. " +
                        "There is no analytics SDK and no crash reporting. Turn auto-check off and " +
                        "the app makes no network calls at all.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
