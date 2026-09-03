package dev.pranav.speed400garage.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.pranav.speed400garage.domain.engine.FillType
import dev.pranav.speed400garage.domain.engine.ValidationResult

/**
 * Fuel entry, ₹-first (§7.2).
 *
 * Amount and rate go in; litres are derived. That is the order an Indian pump works
 * in — you ask for ₹500 of petrol, you do not ask for 4.7 litres — and reversing it
 * would put a small tax on every single fill. The derived litres are shown live so
 * the arithmetic is never a black box.
 *
 * Budget: 3 taps + 2 numbers (§3 P1).
 */
@Composable
fun FuelSheet(
    lastOdometerKm: Int?,
    lastRatePaise: Long?,
    onSave: (odometerKm: Int?, amountPaise: Long, ratePaise: Long?, litres: Double, fillType: FillType, missedPrevious: Boolean, computed: Boolean, notes: String?) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf(lastRatePaise?.let { "%.2f".format(it / 100.0) } ?: "") }
    var odo by remember { mutableStateOf("") }
    var litresOverride by remember { mutableStateOf("") }
    var fillType by remember { mutableStateOf(FillType.FULL) }
    var missed by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }

    val amountPaise = Fmt.parseRupeesToPaise(amount)
    val ratePaise = Fmt.parseRupeesToPaise(rate)
    val typedLitres = litresOverride.trim().toDoubleOrNull()
    val derivedLitres = if (amountPaise != null && ratePaise != null && ratePaise > 0) {
        amountPaise.toDouble() / ratePaise
    } else null
    val litres = typedLitres ?: derivedLitres

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = amount, onValueChange = { amount = it },
                modifier = Modifier.weight(1f), singleLine = true,
                label = { Text("Amount paid ₹") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(
                value = rate, onValueChange = { rate = it },
                modifier = Modifier.weight(1f), singleLine = true,
                label = { Text("Rate ₹/L") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = lastRatePaise?.let { { Text("Last: ${Fmt.rupees(it)}/L") } },
            )
        }

        if (litres != null) {
            Text(
                if (typedLitres != null) "${Fmt.litres(litres)} (as entered)"
                else "= ${Fmt.litres(litres)}",
                style = MaterialTheme.typography.titleLarge,
            )
        }

        OutlinedTextField(
            value = odo, onValueChange = { odo = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Odometer km") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = {
                Text(
                    lastOdometerKm?.let { "Last read ${Fmt.km(it)} — every fill keeps the projection accurate" }
                        ?: "The first reading anchors everything else"
                )
            },
        )

        Text("How full?", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FillType.entries.forEach { type ->
                FilterChip(
                    selected = fillType == type,
                    onClick = { fillType = type },
                    label = {
                        Text(
                            when (type) {
                                FillType.FULL -> "Filled it up"
                                FillType.PARTIAL -> "Partial"
                                FillType.FIRST -> "First ever fill"
                            }
                        )
                    },
                )
            }
        }

        FilterChip(
            selected = missed,
            onClick = { missed = !missed },
            label = { Text("I missed logging a fill before this") },
        )
        if (missed) {
            Text(
                "This breaks the mileage chain rather than reporting a tank that's short " +
                    "by a fill. The next full tank starts a fresh measurement.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = litresOverride, onValueChange = { litresOverride = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Litres (only if the bill disagrees)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        OutlinedTextField(
            value = notes, onValueChange = { notes = it },
            modifier = Modifier.fillMaxWidth(), label = { Text("Notes") },
        )

        Button(
            onClick = {
                onSave(
                    Fmt.parseKm(odo), amountPaise ?: 0L, ratePaise, litres ?: 0.0,
                    fillType, missed, typedLitres == null, notes.ifBlank { null },
                )
            },
            enabled = amountPaise != null && amountPaise > 0 && litres != null && litres > 0,
        ) { Text("Save fill") }
    }
}

/** Arbitrary spend. Budget: 3 taps + 1 number (§3 P1). */
@Composable
fun ExpenseSheet(
    lastOdometerKm: Int?,
    onSave: (odometerKm: Int?, title: String, category: String, amountPaise: Long, notes: String?) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("consumables") }
    var odo by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val amountPaise = Fmt.parseRupeesToPaise(amount)

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = amount, onValueChange = { amount = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Amount ₹") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Text("Category", style = MaterialTheme.typography.bodyMedium)
        CategoryChips(selected = category, onSelect = { category = it })

        OutlinedTextField(
            value = title, onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("What was it?") },
        )
        OutlinedTextField(
            value = odo, onValueChange = { odo = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Odometer km (optional)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = lastOdometerKm?.let { { Text("Last read ${Fmt.km(it)}") } },
        )
        OutlinedTextField(
            value = notes, onValueChange = { notes = it },
            modifier = Modifier.fillMaxWidth(), label = { Text("Notes") },
        )
        Button(
            onClick = { onSave(Fmt.parseKm(odo), title, category, amountPaise ?: 0L, notes.ifBlank { null }) },
            enabled = amountPaise != null && amountPaise > 0,
        ) { Text("Save expense") }
    }
}

@Composable
fun OdometerSheet(lastOdometerKm: Int?, onSave: (Int) -> Unit) {
    var odo by remember { mutableStateOf("") }
    val km = Fmt.parseKm(odo)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = odo, onValueChange = { odo = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Odometer km") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = lastOdometerKm?.let { { Text("Last read ${Fmt.km(it)}") } },
        )
        Button(onClick = { km?.let(onSave) }, enabled = km != null) { Text("Save reading") }
    }
}

/**
 * The questions §9.2 raised.
 *
 * Note the buttons: "Save anyway" is always available unless something blocks, because
 * the app records data it does not understand rather than refusing it.
 */
@Composable
fun ValidationDialog(result: ValidationResult, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (result.canSave) "Before I save that" else "That can't be right") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                (result.blocking + result.questions).forEach { finding ->
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Text(finding.message, style = MaterialTheme.typography.bodyLarge)
                            if (finding.options.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                finding.options.forEach {
                                    Text("· $it", style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (result.canSave) Button(onClick = onConfirm) { Text("Save anyway") }
            else TextButton(onClick = onDismiss) { Text("Let me fix it") }
        },
        dismissButton = if (result.canSave) {
            { TextButton(onClick = onDismiss) { Text("Let me fix it") } }
        } else null,
    )
}

@Composable
private fun CategoryChips(selected: String, onSelect: (String) -> Unit) {
    val categories = listOf(
        "fuel", "labour", "parts", "consumables", "accessories",
        "insurance", "puc", "rto", "washing", "parking", "tolls", "gear", "other",
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c ->
                    FilterChip(
                        selected = selected == c,
                        onClick = { onSelect(c) },
                        label = { Text(c.replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
        }
    }
}
