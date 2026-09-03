package dev.pranav.speed400garage.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.pranav.speed400garage.domain.Provenance
import dev.pranav.speed400garage.ui.ProvenanceBadge

/**
 * Dashboard v1 (§7.1), ordered by what is actually needed on opening the app.
 *
 * Every number carries its provenance: the odometer is 🟡 because it is projected,
 * the mileage and spend are 🔵 because they are computed from logged data. A screen
 * that mixes measured and estimated figures without saying which is which is how a
 * remembered number ends up trusted when it should not be (§3 P4).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardV1(
    snapshot: GarageSnapshot,
    onLogFuel: () -> Unit,
    onLogExpense: () -> Unit,
    onLogOdometer: () -> Unit,
    onLogService: () -> Unit,
    onLogDocument: () -> Unit,
    onLogFault: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Quick actions — never more than one tap away (§7.1).
        // FlowRow, not Row: six buttons fit one line on a tablet and wrap to two or
        // three on a phone. A Row would push the last of them off the screen, which on
        // a phone means the odometer button — the one thing §5.1 needs most.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onLogFuel) { Text("⛽  Fuel") }
            Button(onClick = onLogExpense) { Text("₹  Expense") }
            Button(onClick = onLogService) { Text("🔧  Service / part") }
            OutlinedButton(onClick = onLogOdometer) { Text("🔢  Odometer") }
            OutlinedButton(onClick = onLogDocument) { Text("📄  Document") }
            OutlinedButton(onClick = onLogFault) { Text("⚠  Niggle") }
        }

        Text(snapshot.bikeName ?: "No bike yet", style = MaterialTheme.typography.displaySmall)
        snapshot.registration?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }

        if (!snapshot.hasAnyData) {
            EmptyState(onLogOdometer = onLogOdometer, onLogFuel = onLogFuel)
            return@Column
        }

        // 2. The odometer, with its freshness stated rather than implied (§5.1).
        OdometerCard(snapshot)

        // 3. The numbers that answer "how is it doing".
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatCard(
                label = "Mileage",
                value = Fmt.kmpl(snapshot.economy?.rollingKmpl),
                caption = snapshot.economy?.let {
                    when {
                        it.spans.isEmpty() -> "Needs two full tanks to measure"
                        else -> "Rolling over ${minOf(it.spans.size, it.rollingWindow)} full tanks"
                    }
                } ?: "",
                provenance = Provenance.MY_RECORDS,
            )
            StatCard(
                label = "Last tank",
                value = Fmt.kmpl(snapshot.economy?.latestKmpl),
                caption = "Single tanks are noisy — the rolling figure is the honest one",
                provenance = Provenance.MY_RECORDS,
            )
            StatCard(
                label = "Spent, 30 days",
                value = Fmt.rupees(snapshot.spentThisMonthPaise),
                caption = "${Fmt.rupees(snapshot.totalSpentPaise)} all time",
                provenance = Provenance.MY_RECORDS,
            )
        }

        // Folded in from what used to be its own tab. "Can I ride tomorrow?" is a
        // home-screen question, not a destination you navigate to.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                dev.pranav.speed400garage.ui.due.ReadinessCard()
            }
        }

        snapshot.cost?.let { CostCard(it) }
    }
}

@Composable
private fun OdometerCard(snapshot: GarageSnapshot) {
    val p = snapshot.projection
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Odometer", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (p == null) {
                Text("Not known yet", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Log a reading and everything else — service intervals, cost per km, " +
                        "mileage — starts working.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    if (p.hasRate) "≈ ${Fmt.km(p.estimatedKm)}" else Fmt.km(p.estimatedKm),
                    style = MaterialTheme.typography.displaySmall,
                )
                Text(
                    buildString {
                        append("Last read ${Fmt.km(p.lastReading.km)}, ${Fmt.daysAgo(p.daysSinceReading)}")
                        if (p.hasRate) append(" · %.0f km/day".format(p.kmPerDay))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                ProvenanceBadge(if (p.hasRate) Provenance.ESTIMATE else Provenance.MY_RECORDS)
                if (p.isStale) {
                    Text(
                        "That estimate is over two weeks old, so km-based reminders are " +
                            "guesswork until you log a reading.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}

/** The three ₹/km numbers, always together and always labelled (§9.3). */
@Composable
private fun CostCard(cost: dev.pranav.speed400garage.domain.engine.CostPerKm) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cost per kilometre", style = MaterialTheme.typography.titleLarge)
            Text(
                "Three different numbers, never one. A bare \"cost per km\" is ambiguous " +
                    "enough to be useless.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            CostRow("Fuel only", Fmt.rupeesPerKm(cost.fuelPaisePerKm), "last 30 days", Provenance.MY_RECORDS)
            CostRow("Running", Fmt.rupeesPerKm(cost.runningPaisePerKm), "fuel, service, parts, consumables · last 30 days", Provenance.MY_RECORDS)
            CostRow(
                "True cost",
                Fmt.rupeesPerKm(cost.truePaisePerKm),
                if (cost.includesDepreciation) "everything including depreciation · since purchase"
                else "everything EXCEPT depreciation — add a purchase price to include it",
                if (cost.includesDepreciation) Provenance.ESTIMATE else Provenance.MY_RECORDS,
            )
            if (cost.distanceKm == 0) {
                Text(
                    "Needs two odometer readings 30 days apart before these mean anything.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun CostRow(label: String, value: String, caption: String, provenance: Provenance) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(caption, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.titleLarge)
        ProvenanceBadge(provenance)
    }
}

@Composable
private fun StatCard(label: String, value: String, caption: String, provenance: Provenance) {
    // A range rather than a fixed width, so three sit side by side on a tablet and
    // stack readably on a phone instead of being clipped.
    Card(Modifier.widthIn(min = 170.dp, max = 280.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.displaySmall)
            ProvenanceBadge(provenance)
            Text(caption, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The first-run screen.
 *
 * Value compounds with elapsed time (§13): the most useful thing this screen can do
 * is get one odometer reading in today rather than explain the app.
 */
@Composable
private fun EmptyState(onLogOdometer: () -> Unit, onLogFuel: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Nothing logged yet", style = MaterialTheme.typography.titleLarge)
            Text(
                "Start with today's odometer — it anchors service intervals, cost per km " +
                    "and the mileage engine. Then log your next fill at the pump and the " +
                    "app takes it from there.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Mileage needs two full tanks before it can report anything, so the sooner " +
                    "the first one goes in, the sooner the number means something.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onLogOdometer) { Text("Log today's odometer") }
                OutlinedButton(onClick = onLogFuel) { Text("Log a fill") }
            }
        }
    }
}
