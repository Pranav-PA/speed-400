package dev.pranav.speed400garage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * A GitHub token is NOT needed while the repository is public — this stays collapsed
 * behind a link so it does not read like a setup step. It exists only so that making
 * the repository private later does not break updates.
 */
@Composable
fun OptionalTokenField(onSave: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    if (!expanded) {
        TextButton(onClick = { expanded = true }) { Text("Repository is private? Add a token") }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Only needed if the repository is private. A fine-grained token with " +
                "Contents: Read-only on this repository is enough. Stored encrypted on this tablet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                label = { Text("GitHub token") },
            )
            Button(onClick = { onSave(draft); draft = ""; expanded = false }, enabled = draft.isNotBlank()) {
                Text("Save")
            }
            TextButton(onClick = { onSave(null); draft = ""; expanded = false }) { Text("Clear") }
        }
    }
}
