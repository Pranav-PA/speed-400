package dev.pranav.speed400garage.ui.assistant

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Importing the handbook (§10.2).
 *
 * Deliberately explains what the import buys, because "import a PDF" is not obviously
 * worth doing until you know it is the difference between the assistant answering
 * specifications and answering procedures.
 */
@Composable
fun HandbookCard(viewModel: HandbookViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Owner's handbook", style = MaterialTheme.typography.titleLarge)

            if (state.chunkCount > 0) {
                Text(
                    "${state.chunkCount} passages indexed. The assistant can answer procedure " +
                        "questions — how to adjust the chain, what the warning lights mean — and " +
                        "cites the page every time.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    "Not imported yet. Specifications already work from the built-in fact table, " +
                        "but procedures — \"how do I adjust the chain\" — need the full handbook.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Download it from triumphtechnicalinformation.com/handbooks, then pick the PDF " +
                        "here. It is indexed on this tablet and searched offline — the PDF is never " +
                        "uploaded anywhere.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.importing) {
                LinearProgressIndicator(
                    progress = { if (state.totalPages > 0) state.pagesDone.toFloat() / state.totalPages else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Reading page ${state.pagesDone} of ${state.totalPages}…", style = MaterialTheme.typography.bodyMedium)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { picker.launch(arrayOf("application/pdf")) },
                    enabled = !state.importing,
                ) { Text(if (state.chunkCount > 0) "Re-import" else "Import the handbook PDF") }
                if (state.chunkCount > 0 && !state.importing) {
                    TextButton(onClick = viewModel::clear) { Text("Remove") }
                }
            }

            state.message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}
