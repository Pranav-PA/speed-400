package dev.pranav.speed400garage.ui.chart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pranav.speed400garage.domain.Provenance
import dev.pranav.speed400garage.ui.ProvenanceBadge
import dev.pranav.speed400garage.ui.log.Fmt
import kotlin.math.roundToInt

/**
 * Analytics (§11).
 *
 * Only the charts in that table, and each one leads with the question it answers
 * rather than a chart title. If a question could not be written down first, the chart
 * did not ship — a screen full of visualisations nobody asked for is worse than three
 * that answer something.
 *
 * Deliberately absent, per §11: fuel-price-over-time (you don't control it), speed or
 * pace analytics, goals, gamification, and comparison against other riders.
 */
@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Text("Analytics", style = MaterialTheme.typography.displaySmall)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                ChartFrame(
                    question = "Is my mileage getting worse?",
                    window = "Every measured full-to-full tank",
                    takeaway = mileageTakeaway(state),
                ) { TrendChart(raw = state.tankKmpl, trend = state.rollingKmpl) }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                ChartFrame(
                    question = "Am I riding more or less?",
                    window = "Last 12 months, from logged odometer readings",
                    takeaway = ridingTakeaway(state),
                ) {
                    ColumnChart(
                        values = state.kmPerMonth.map { it.value },
                        labels = state.kmPerMonth.map { it.label },
                        valueLabel = { "${it.roundToInt()}" },
                    )
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                ChartFrame(
                    question = "Where does the money actually go?",
                    window = "Everything logged, all time",
                    takeaway = categoryTakeaway(state),
                ) {
                    RankedBars(
                        state.categories.map {
                            Triple(
                                it.category.replaceFirstChar { c -> c.uppercase() },
                                it.paise.toDouble(),
                                "${Fmt.rupees(it.paise)}  ${(it.share * 100).roundToInt()}%",
                            )
                        }
                    )
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                ChartFrame(
                    question = "Which month is expensive?",
                    window = "Last 12 months",
                    takeaway = spendTakeaway(state),
                ) {
                    ColumnChart(
                        values = state.spendPerMonth.map { it.value },
                        labels = state.spendPerMonth.map { it.label },
                        valueLabel = { Fmt.rupees(it.toLong()) },
                    )
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("What's my real running cost?", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Three numbers, never one — they differ by roughly an order of magnitude " +
                        "in early ownership, so a bare \"cost per km\" is not an answer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.cost?.let { cost ->
                    CostLine("Fuel only", Fmt.rupeesPerKm(cost.fuelPaisePerKm), "last 12 months", Provenance.MY_RECORDS)
                    CostLine("Running", Fmt.rupeesPerKm(cost.runningPaisePerKm), "fuel, service, parts, consumables · last 12 months", Provenance.MY_RECORDS)
                    CostLine(
                        "True cost",
                        Fmt.rupeesPerKm(cost.truePaisePerKm),
                        if (cost.includesDepreciation) "everything including depreciation · since purchase"
                        else "everything EXCEPT depreciation — set a purchase price to include it",
                        if (cost.includesDepreciation) Provenance.ESTIMATE else Provenance.MY_RECORDS,
                    )
                }
                Text(
                    "Lifetime spend ${Fmt.rupees(state.lifetimePaise)}",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("How far do I get on a tank?", style = MaterialTheme.typography.titleLarge)
                if (state.usableRangeKm == null) {
                    Text(
                        "Needs two full tanks before this means anything.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("≈ ${Fmt.km(state.usableRangeKm!!)} before the low-fuel light", style = MaterialTheme.typography.displaySmall)
                    ProvenanceBadge(Provenance.MY_RECORDS)
                    Text(
                        "${Fmt.km(state.fullTankRangeKm ?: 0)} on the full 13 litres, but the light comes " +
                            "on with 3 litres left (handbook p.199) — plan on the first number.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.spans.takeIf { it.isNotEmpty() }?.let { spans ->
                        Text(
                            "Measured tanks have run ${spans.minOf { it.km }}–${spans.maxOf { it.km }} km.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CostLine(label: String, value: String, caption: String, provenance: Provenance) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.titleLarge)
        ProvenanceBadge(provenance)
    }
}

// Takeaways are written in plain language because a chart you have to interpret from
// scratch every time is a chart you stop opening (§11).

private fun mileageTakeaway(state: AnalyticsState): String? {
    if (state.rollingKmpl.size < 2) return null
    val latest = state.rollingKmpl.last()
    val earlier = state.rollingKmpl.first()
    val change = (latest - earlier) / earlier * 100
    return when {
        kotlin.math.abs(change) < 5 -> "Holding steady at about %.1f km/l.".format(latest)
        change < 0 -> "Down about %.0f%% since you started logging — now around %.1f km/l.".format(-change, latest)
        else -> "Up about %.0f%% since you started logging — now around %.1f km/l.".format(change, latest)
    }
}

private fun ridingTakeaway(state: AnalyticsState): String? {
    val months = state.kmPerMonth.filter { it.value > 0 }
    if (months.isEmpty()) return null
    val busiest = months.maxBy { it.value }
    val average = months.sumOf { it.value } / months.size
    return "Averaging ${average.roundToInt()} km a month; busiest was ${busiest.label} at ${busiest.value.roundToInt()} km."
}

private fun categoryTakeaway(state: AnalyticsState): String? {
    val top = state.categories.firstOrNull() ?: return null
    return "${top.category.replaceFirstChar { it.uppercase() }} is ${(top.share * 100).roundToInt()}% of everything you've spent."
}

private fun spendTakeaway(state: AnalyticsState): String? {
    val months = state.spendPerMonth.filter { it.value > 0 }
    if (months.isEmpty()) return null
    val worst = months.maxBy { it.value }
    return "${worst.label} was the expensive one at ${Fmt.rupees(worst.value.toLong())}."
}
