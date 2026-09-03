package dev.pranav.speed400garage.ui.due

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pranav.speed400garage.domain.Provenance
import dev.pranav.speed400garage.domain.engine.DueItem
import dev.pranav.speed400garage.domain.engine.NotifyClass
import dev.pranav.speed400garage.domain.engine.Severity
import dev.pranav.speed400garage.ui.EmptyDetail
import dev.pranav.speed400garage.ui.ListDetailPane
import dev.pranav.speed400garage.ui.ProvenanceBadge
import dev.pranav.speed400garage.ui.SectionHeading
import dev.pranav.speed400garage.ui.chart.ChartTokens
import dev.pranav.speed400garage.ui.log.Fmt

/**
 * What wants doing, and why (§8).
 *
 * The light checks sit in their own section rather than at the bottom of the same
 * list — mixing "chain lube" into the same ranking as "insurance expires in 3 days"
 * is how a reminder list stops being read.
 */
@Composable
fun DueScreen(viewModel: DueViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<DueItem?>(null) }

    val scheduled = state.items.filter { it.notifyClass != NotifyClass.DIGEST }
    val digest = state.items.filter { it.notifyClass == NotifyClass.DIGEST }

    ListDetailPane(
        hasSelection = selected != null,
        onBack = { selected = null },
        list = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    Column {
                        SectionHeading("Due next")
                        state.stalenessNudge?.let {
                            Card(Modifier.fillMaxWidth()) {
                                Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                        if (scheduled.isEmpty()) {
                            Text(
                                "Nothing scheduled yet. Log a service or an odometer reading and the " +
                                    "handbook intervals start counting.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                items(scheduled, key = { it.key }) { item ->
                    DueRow(item) { selected = item }
                    HorizontalDivider()
                }

                if (digest.isNotEmpty()) {
                    item {
                        Column(Modifier.padding(top = 20.dp)) {
                            SectionHeading("Every ride")
                            Text(
                                "These never notify individually — they'd train you to swipe " +
                                    "everything away. They arrive as one weekly digest.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    items(digest, key = { it.key }) { item ->
                        DueRow(item) { selected = item }
                        HorizontalDivider()
                    }
                }

                if (state.openFaults.isNotEmpty()) {
                    item {
                        Column(Modifier.padding(top = 20.dp)) {
                            SectionHeading("Open niggles (${state.openFaults.size})")
                            Text(
                                "Read these out at the service counter.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    items(state.openFaults, key = { it.id }) { fault ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(fault.summary, style = MaterialTheme.typography.bodyLarge)
                                fault.firstNoticedOdometerKm?.let {
                                    Text(
                                        "First noticed at ${Fmt.km(it)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            TextButton(onClick = { viewModel.closeFault(fault.id) }) { Text("Close") }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        detail = {
            val item = selected
            if (item == null) {
                EmptyDetail("Pick something to see when it's due and where the interval came from.")
            } else {
                DueDetail(item)
            }
        },
    )
}

@Composable
private fun DueRow(item: DueItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(10.dp).clip(CircleShape).background(colourFor(item.severity)),
        )
        Column(Modifier.weight(1f)) {
            Text(item.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                summaryOf(item),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DueDetail(item: DueItem) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(item.label, style = MaterialTheme.typography.displaySmall)
        Text(summaryOf(item), style = MaterialTheme.typography.bodyLarge)

        ProvenanceBadge(Provenance.fromSource(item.intervalSource), item.manualPageRef)

        item.dueOdometerKm?.let { Text("Due at ${Fmt.km(it)}", style = MaterialTheme.typography.bodyLarge) }
        item.dueDay?.let { Text("Projected ${Fmt.date(it)}", style = MaterialTheme.typography.bodyLarge) }

        if (item.isProjected) {
            Text(
                "That date comes from your riding rate, not a calendar — it moves as you ride.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (item.isStale) {
            Text(
                "The odometer estimate behind this is over two weeks old, so treat the date " +
                    "as a guess until you log a reading.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        if (item.isWarrantyRelevant) {
            Text(
                "Warranty-relevant. Warranty terms generally require scheduled services on " +
                    "time at an authorised centre, with proof — keep the invoice.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        if (item.notifyClass == NotifyClass.DIGEST) {
            Text(
                "A pre-ride check. It appears in the weekly digest, never as its own notification.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun summaryOf(item: DueItem): String = buildString {
    when {
        item.notifyClass == NotifyClass.DIGEST -> append("Every ride")
        item.severity == Severity.OVERDUE -> {
            append("Overdue")
            item.daysRemaining?.let { append(" by ${-it} days") }
        }
        item.kmRemaining != null && item.daysRemaining != null ->
            append("${Fmt.km(item.kmRemaining)} or ${item.daysRemaining} days away")
        item.kmRemaining != null -> append("${Fmt.km(item.kmRemaining)} away")
        item.daysRemaining != null -> append("${item.daysRemaining} days away")
        else -> append("No date yet")
    }
    if (item.isStale) append(" · estimate is stale")
}

/**
 * Three colours for four severities, deliberately.
 *
 * Four warm steps were tried and failed validation: amber, orange and red sat at
 * ΔE 10 or below for normal vision — under the floor of 15 — so a reader could not
 * reliably tell DUE from OVERDUE by colour anyway. The row text already distinguishes
 * them ("Overdue by 12 days" versus "300 km away"), so the honest encoding is three
 * colours that genuinely separate, with the text carrying the fourth distinction.
 *
 * These three pass every check against both surfaces — see [ChartTokens].
 */
@Composable
private fun colourFor(severity: Severity): Color = when (severity) {
    Severity.OVERDUE, Severity.DUE -> ChartTokens.critical()
    Severity.DUE_SOON -> ChartTokens.warning
    Severity.INFO -> ChartTokens.good
}
