package dev.pranav.speed400garage.ui.due

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pranav.speed400garage.domain.engine.NotifyClass
import dev.pranav.speed400garage.domain.engine.Severity
import dev.pranav.speed400garage.ui.chart.AnalyticsViewModel
import dev.pranav.speed400garage.ui.log.Fmt

/**
 * "Can I ride to Coorg tomorrow?" (§5.5)
 *
 * The payoff screen — it exists only *because* everything else is tracked, and it is
 * mostly a rearrangement of data the app already has. One deliberate design choice:
 * it distinguishes ✅ / ⚠️ / ❓, and the ❓ is not decoration. "I don't know when the
 * tyre pressure was last checked" is genuinely different information from "it's fine",
 * and collapsing the two would be the app quietly pretending to know things.
 */
@Composable
fun ReadinessCard(
    dueViewModel: DueViewModel = hiltViewModel(),
    analyticsViewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val due by dueViewModel.state.collectAsStateWithLifecycle()
    val analytics by analyticsViewModel.state.collectAsStateWithLifecycle()

    val lines = buildList {
        // Documents first — the ones that make riding legal.
        val documents = due.items.filter { it.notifyClass == NotifyClass.DOCUMENT }
        if (documents.isEmpty()) {
            add(Line("❓", "No documents on file — add insurance and PUC so this screen can check them."))
        } else {
            documents.forEach { doc ->
                val days = doc.daysRemaining
                when {
                    days == null -> add(Line("❓", "${doc.label} — no expiry recorded"))
                    days < 0 -> add(Line("⚠️", "${doc.label} EXPIRED ${-days} days ago"))
                    days <= 30 -> add(Line("⚠️", "${doc.label} expires in $days days — sort it before you go"))
                    else -> add(Line("✅", "${doc.label} valid until ${doc.dueDay?.let(Fmt::date)}"))
                }
            }
        }

        // Then anything mechanical that is close or past.
        due.items.filter { it.notifyClass != NotifyClass.DOCUMENT && it.notifyClass != NotifyClass.DIGEST }
            .filter { it.severity >= Severity.DUE_SOON }
            .take(4)
            .forEach { item ->
                add(Line("⚠️", "${item.label} — ${describe(item.kmRemaining, item.daysRemaining, item.severity)}"))
            }

        val nextService = due.items.firstOrNull {
            it.notifyClass != NotifyClass.DIGEST && it.severity < Severity.DUE_SOON && it.kmRemaining != null
        }
        nextService?.let {
            add(Line("✅", "${it.label} not due for ${Fmt.km(it.kmRemaining!!)}"))
        }

        // The daily checks are exactly the things nobody remembers the state of.
        due.items.filter { it.notifyClass == NotifyClass.DIGEST }.take(2).forEach {
            add(Line("❓", "${it.label} — the app can't know when you last did this"))
        }

        // Range, which is what actually decides whether a day's ride is one fuel stop.
        analytics.usableRangeKm?.let {
            add(Line("✅", "Fuel range ≈ ${Fmt.km(it)} from full before the low-fuel light"))
        } ?: add(Line("❓", "Fuel range unknown — needs two full tanks logged"))

        due.stalenessNudge?.let { add(Line("⚠️", it)) }
    }

    val blockers = lines.count { it.symbol == "⚠️" }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ready to ride?", style = MaterialTheme.typography.titleLarge)
        Text(
            when {
                blockers == 0 -> "Nothing standing in your way."
                blockers == 1 -> "One thing to deal with first."
                else -> "$blockers things to deal with first."
            },
            style = MaterialTheme.typography.bodyLarge,
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                lines.forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(line.symbol, style = MaterialTheme.typography.bodyLarge)
                        Text(line.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        if (due.openFaults.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Open niggles worth knowing about", style = MaterialTheme.typography.titleLarge)
                    due.openFaults.forEach {
                        Text("· ${it.summary}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Text(
            "Documents are archived here, but at a checkpoint use DigiLocker or mParivahan " +
                "on your phone — that is the legally accepted digital route in India, and this " +
                "app should not pretend otherwise.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class Line(val symbol: String, val text: String)

private fun describe(kmRemaining: Int?, daysRemaining: Int?, severity: Severity): String = when {
    severity == Severity.OVERDUE && kmRemaining != null -> "overdue by ${Fmt.km(-kmRemaining)}"
    severity == Severity.OVERDUE && daysRemaining != null -> "overdue by ${-daysRemaining} days"
    kmRemaining != null -> "due in ${Fmt.km(kmRemaining)}"
    daysRemaining != null -> "due in $daysRemaining days"
    else -> "due"
}
