package dev.pranav.speed400garage.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pranav.speed400garage.ui.GarageViewModel

/**
 * A workshop visit or a part replacement.
 *
 * This is the sheet that makes §3 P2 concrete. Replacing the front brake pads at a
 * service is ONE entry: it records what was spent, and it resets the front brake pad
 * component at that odometer. Both facts come from one form, so the spend total and
 * the maintenance state cannot disagree about whether it happened.
 *
 * The component list is the catalogue seeded from the handbook in Phase 0, so ticking
 * "Front brake pads" here is what will later let the app say when they are next due.
 */
@Composable
fun ServiceSheet(
    lastOdometerKm: Int?,
    // Declared before onSave so the trailing lambda at the call site binds to onSave.
    catalogue: GarageViewModel = hiltViewModel(),
    onSave: (
        odometerKm: Int?,
        title: String,
        vendor: String?,
        lines: List<Pair<String, Long>>,
        componentKeys: List<String>,
        action: String,
        notes: String?,
    ) -> Unit,
) {
    val components by catalogue.components.collectAsStateWithLifecycle()

    var title by remember { mutableStateOf("") }
    var vendor by remember { mutableStateOf("") }
    var odo by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var action by remember { mutableStateOf("replaced") }
    var search by remember { mutableStateOf("") }

    val amounts = remember { mutableStateMapOf<String, String>() }
    val selected = remember { mutableStateMapOf<String, Boolean>() }

    val lines = MONEY_CATEGORIES.mapNotNull { category ->
        Fmt.parseRupeesToPaise(amounts[category].orEmpty())?.takeIf { it > 0 }?.let { category to it }
    }
    val chosen = selected.filterValues { it }.keys.toList()
    val total = lines.sumOf { it.second }

    val matching = remember(components, search) {
        if (search.isBlank()) components
        else components.filter { it.displayName.contains(search, ignoreCase = true) }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = title, onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("What was done?") },
            placeholder = { Text("First service, brake pads, chain clean…") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = vendor, onValueChange = { vendor = it },
                modifier = Modifier.weight(1f), singleLine = true,
                label = { Text("Workshop") },
            )
            OutlinedTextField(
                value = odo, onValueChange = { odo = it },
                modifier = Modifier.weight(1f), singleLine = true,
                label = { Text("Odometer km") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = lastOdometerKm?.let { { Text("Last ${Fmt.km(it)}") } },
            )
        }

        Text("What it cost", style = MaterialTheme.typography.titleLarge)
        Text(
            "Split it out if the invoice does. Every figure the app reports later is a sum " +
                "over these rows, never a sum of visits, so a service entered once cannot be " +
                "double-counted.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MONEY_CATEGORIES.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { category ->
                    OutlinedTextField(
                        value = amounts[category].orEmpty(),
                        onValueChange = { amounts[category] = it },
                        modifier = Modifier.weight(1f), singleLine = true,
                        label = { Text("${category.replaceFirstChar { it.uppercase() }} ₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                if (row.size == 1) Column(Modifier.weight(1f)) {}
            }
        }
        if (total > 0) {
            Text("Total ${Fmt.rupees(total)}", style = MaterialTheme.typography.titleLarge)
        }

        Text("What it touched", style = MaterialTheme.typography.titleLarge)
        Text(
            "Ticking a component here is what resets its interval. This is the difference " +
                "between a receipt and a service history.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("replaced", "serviced", "checked", "adjusted", "topped_up").forEach { a ->
                FilterChip(
                    selected = action == a,
                    onClick = { action = a },
                    label = { Text(a.replace('_', ' ').replaceFirstChar { it.uppercase() }) },
                )
            }
        }
        OutlinedTextField(
            value = search, onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Find a component") },
            placeholder = { Text("brake, chain, oil…") },
        )
        Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
            matching.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { component ->
                        FilterChip(
                            selected = selected[component.key] == true,
                            onClick = { selected[component.key] = selected[component.key] != true },
                            label = { Text(component.displayName) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Column(Modifier.weight(1f)) {}
                }
            }
        }
        if (chosen.isNotEmpty()) {
            Text("${chosen.size} selected", style = MaterialTheme.typography.bodyMedium)
        }

        OutlinedTextField(
            value = notes, onValueChange = { notes = it },
            modifier = Modifier.fillMaxWidth(), label = { Text("Notes") },
        )

        Button(
            onClick = {
                onSave(
                    Fmt.parseKm(odo), title, vendor.ifBlank { null },
                    lines, chosen, action, notes.ifBlank { null },
                )
            },
            enabled = title.isNotBlank() || total > 0 || chosen.isNotEmpty(),
        ) { Text("Save") }
    }
}

private val MONEY_CATEGORIES = listOf("labour", "parts", "consumables", "other")
