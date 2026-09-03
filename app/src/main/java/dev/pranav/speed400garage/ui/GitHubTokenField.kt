package dev.pranav.speed400garage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
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

@Composable
fun GitHubTokenField(hasToken: Boolean, onSave: (String?) -> Unit) {
    var draft by remember { mutableStateOf("") }

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
            label = { Text(if (hasToken) "Replace saved token" else "GitHub token (github_pat_…)") },
        )
        Button(onClick = { onSave(draft); draft = "" }, enabled = draft.isNotBlank()) { Text("Save") }
        if (hasToken) TextButton(onClick = { onSave(null); draft = "" }) { Text("Clear") }
    }
}
