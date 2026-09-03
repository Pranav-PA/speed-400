package dev.pranav.speed400garage.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pranav.speed400garage.ai.Exchange
import dev.pranav.speed400garage.ui.ProvenanceBadge

/**
 * The assistant (§10).
 *
 * Every answer carries its provenance, and the screen says outright which questions
 * were answered without any of your data leaving the tablet — because "the model
 * plans, the device computes" is only reassuring if you can see it happening.
 */
@Composable
fun AssistantScreen(viewModel: AssistantViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var question by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Assistant", style = MaterialTheme.typography.displaySmall)

        if (!state.hasKey || state.model == null) {
            SetupCard(state, viewModel)
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.history.isEmpty()) {
                item { Suggestions { question = it } }
            }
            items(state.history) { ExchangeCard(it) }
            if (state.thinking) {
                item { Text("Thinking…", style = MaterialTheme.typography.bodyMedium) }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                modifier = Modifier.weight(1f),
                label = { Text("Ask about the bike") },
                placeholder = { Text("What's the rear tyre pressure? · How much have I spent on fuel?") },
            )
            Button(
                onClick = { viewModel.ask(question); question = "" },
                enabled = question.isNotBlank() && !state.thinking,
            ) { Text("Ask") }
            if (state.history.isNotEmpty()) {
                TextButton(onClick = viewModel::clear) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun ExchangeCard(exchange: Exchange) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                exchange.question,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(exchange.answer, style = MaterialTheme.typography.bodyLarge)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProvenanceBadge(exchange.provenance, exchange.pageRef)
                if (exchange.computedOnDevice && !exchange.blocked) {
                    Text(
                        "computed on this tablet",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (exchange.blocked) {
                Text(
                    "I had an answer but couldn't trace every number in it to the handbook, and this " +
                        "is a safety-critical question — so I threw it away rather than show you a " +
                        "figure I can't stand behind.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            if (exchange.downgradedNumbers.isNotEmpty()) {
                Text(
                    "Couldn't trace ${exchange.downgradedNumbers.joinToString(", ")} back to a source, " +
                        "so treat this as general rather than verified.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun Suggestions(onPick: (String) -> Unit) {
    val examples = listOf(
        "What's the rear tyre pressure?",
        "What oil does it take?",
        "How much have I spent on fuel?",
        "When was the engine oil last changed?",
        "What's my mileage?",
        "What's due?",
        "Torque for the oil drain plug?",
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Try:", style = MaterialTheme.typography.bodyMedium)
        examples.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { FilterChip(selected = false, onClick = { onPick(it) }, label = { Text(it) }) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Specifications come from the handbook with a page number. Questions about your " +
                "own records are computed here on the tablet — only the question text and the " +
                "list of tool names ever reach Google, never your figures.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SetupCard(state: AssistantState, viewModel: AssistantViewModel) {
    var key by remember { mutableStateOf("") }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Set up the assistant", style = MaterialTheme.typography.titleLarge)

            if (!state.hasKey) {
                Text(
                    "Paste a Gemini API key from aistudio.google.com. It's stored encrypted on this " +
                        "tablet and never leaves it except to talk to Google.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = key, onValueChange = { key = it },
                        modifier = Modifier.weight(1f), singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text("Gemini API key") },
                    )
                    Button(onClick = { viewModel.setKey(key); key = "" }, enabled = key.isNotBlank()) {
                        Text("Save")
                    }
                }
                Text(
                    "On the free tier Google may use prompts to train its models, and India isn't " +
                        "covered by the regional carve-out. That's why record questions are computed " +
                        "here and never sent — but enabling billing on the key removes the question " +
                        "entirely, and at a handful of requests a day it costs approximately nothing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            } else {
                Text(
                    "Now pick a model. The app asks Google which ones your key can use rather than " +
                        "assuming — the IDs written down when this app was planned have already been retired.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = viewModel::refreshModels) { Text("Fetch available models") }
                state.modelError?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                }
                state.availableModels.filter { it.contains("flash") }.take(6).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { model ->
                            FilterChip(
                                selected = state.model == model,
                                onClick = { viewModel.setModel(model) },
                                label = { Text(model) },
                            )
                        }
                    }
                }
                if (state.availableModels.isNotEmpty()) {
                    Text(
                        "Flash-tier models are shown — they're fast, cheap, and this app only asks " +
                            "the model to pick a tool, which is not hard work.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { viewModel.setKey(null) }) { Text("Remove key") }
            }
        }
    }
}
