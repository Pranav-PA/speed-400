package dev.pranav.speed400garage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import dev.pranav.speed400garage.domain.Provenance

/**
 * Phase 0's dashboard: enough to prove the criterion in §15 — "the app opens, knows the
 * bike exists, and every interval in it traces to a page number in the handbook."
 *
 * The quick-action row, pulse, due-next and recent-activity blocks from §7.1 are
 * Phase 1 work and are deliberately absent rather than faked.
 */
@Composable
fun DashboardScreen(viewModel: GarageViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            state.bikeName ?: "No bike yet",
            style = MaterialTheme.typography.displaySmall,
        )
        state.registration?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }

        Text(
            "Ground truth: Owner's Handbook ${state.handbookPartNumber ?: "—"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard("Components", "${state.componentCount}", "${state.componentsCitedToManual} cite a handbook page")
            StatCard("Facts", "${state.factCount}", "${state.factsVerifiedByOwner} verified by you")
            StatCard("Events logged", "${state.eventCount}", "Phase 1 starts filling this")
            StatCard("Capture inbox", "${state.inboxPending}", "Phase 1")
        }

        Spacer(Modifier.height(8.dp))

        // §10.3 is explicit that an unverified row is better than a wrong one, and that
        // rows are promoted by the owner's own eyes rather than by an extraction pass.
        if (state.factsVerifiedByOwner < state.factCount) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Seed data is not owner-verified yet", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Every fact below was read out of the handbook PDF's text layer and carries a " +
                            "page number, but none has been checked against the rendered page by you. " +
                            "Until it is, the assistant treats safety-critical values as unconfirmed.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    ProvenanceBadge(Provenance.MANUAL, pageRef = null)
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, caption: String) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(4.dp))
            Text(caption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
