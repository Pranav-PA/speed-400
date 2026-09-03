package dev.pranav.speed400garage.ui.due

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.pranav.speed400garage.ui.log.Fmt
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * A document with its expiry (§7.6).
 *
 * Insurance gets two expiry fields, because Indian new-vehicle cover routinely bundles
 * a multi-year third-party policy with an own-damage policy renewed annually. A single
 * expiry field would silently produce a reminder for the wrong one — and the one it
 * would miss is the one that lapses first.
 */
@Composable
fun DocumentSheet(
    onSave: (
        docType: String, issuer: String?, number: String?,
        expiresOn: Long?, secondaryExpiresOn: Long?, amountPaise: Long?, notes: String?,
    ) -> Unit,
) {
    var docType by remember { mutableStateOf("insurance") }
    var issuer by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var secondaryExpiry by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val expiryDay = parseDate(expiry)
    val secondaryDay = parseDate(secondaryExpiry)

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Type", style = MaterialTheme.typography.bodyMedium)
        DOC_TYPES.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t ->
                    FilterChip(docType == t, { docType = t }, { Text(label(t)) })
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = issuer, onValueChange = { issuer = it },
                modifier = Modifier.weight(1f), singleLine = true,
                label = { Text("Issuer") },
            )
            OutlinedTextField(
                value = number, onValueChange = { number = it },
                modifier = Modifier.weight(1f), singleLine = true,
                label = { Text("Policy / certificate number") },
            )
        }

        OutlinedTextField(
            value = expiry, onValueChange = { expiry = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(if (docType == "insurance") "Own-damage cover expires" else "Expires") },
            placeholder = { Text("2027-03-14") },
            isError = expiry.isNotBlank() && expiryDay == null,
            supportingText = { Text("YYYY-MM-DD") },
        )

        if (docType == "insurance") {
            OutlinedTextField(
                value = secondaryExpiry, onValueChange = { secondaryExpiry = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Third-party cover expires (if different)") },
                placeholder = { Text("2029-03-14") },
                isError = secondaryExpiry.isNotBlank() && secondaryDay == null,
                supportingText = {
                    Text(
                        "New-vehicle policies often bundle multi-year third-party cover with " +
                            "annually-renewed own-damage cover. Both get their own reminder."
                    )
                },
            )
        }

        OutlinedTextField(
            value = amount, onValueChange = { amount = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Premium paid ₹") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            supportingText = { Text("Flows straight into your spend — no need to log it twice.") },
        )
        OutlinedTextField(
            value = notes, onValueChange = { notes = it },
            modifier = Modifier.fillMaxWidth(), label = { Text("Notes") },
        )

        Button(
            onClick = {
                onSave(
                    docType, issuer.ifBlank { null }, number.ifBlank { null },
                    expiryDay, secondaryDay,
                    Fmt.parseRupeesToPaise(amount), notes.ifBlank { null },
                )
            },
            enabled = expiry.isBlank() || expiryDay != null,
        ) { Text("Save document") }
    }
}

/** A niggle (§5.4) — an open issue, not an expense. */
@Composable
fun FaultSheet(lastOdometerKm: Int?, onSave: (summary: String, odometerKm: Int?, notes: String?) -> Unit) {
    var summary by remember { mutableStateOf("") }
    var odo by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = summary, onValueChange = { summary = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("What's it doing?") },
            placeholder = { Text("Rattle from the left side around 4,000 rpm") },
        )
        OutlinedTextField(
            value = odo, onValueChange = { odo = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Odometer km") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = lastOdometerKm?.let { { Text("Last ${Fmt.km(it)}") } },
        )
        OutlinedTextField(
            value = notes, onValueChange = { notes = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("When does it happen?") },
            placeholder = { Text("Only when cold, only above 60 km/h…") },
        )
        Text(
            "This stays open until a service closes it. The list gets read out at the counter, " +
                "which is the whole point — by then you'd have forgotten three of the four.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { onSave(summary, Fmt.parseKm(odo), notes.ifBlank { null }) },
            enabled = summary.isNotBlank(),
        ) { Text("Log it") }
    }
}

private val DOC_TYPES = listOf(
    "insurance", "puc", "rc", "licence", "warranty", "invoice", "service_plan", "rsa", "loan", "other",
)

private fun label(type: String) = when (type) {
    "puc" -> "PUC"
    "rc" -> "RC"
    "rsa" -> "RSA"
    "service_plan" -> "Service plan"
    else -> type.replaceFirstChar { it.uppercase() }
}

/** Epoch millis at local midnight, or null if the text is not a date yet. */
private fun parseDate(text: String): Long? = try {
    if (text.isBlank()) null
    else LocalDate.parse(text.trim())
        .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
} catch (e: DateTimeParseException) {
    null
}
