package dev.pranav.speed400garage.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pranav.speed400garage.data.db.entity.FactEntity
import dev.pranav.speed400garage.domain.Provenance
import dev.pranav.speed400garage.domain.SafetyRule

/**
 * Plan §10.3 job 1 — a Quick Specs screen with no AI and no network, instant.
 *
 * This screen is the offline answer to "what's the rear tyre pressure", which is
 * exactly the question that gets asked where there is no signal.
 */
@Composable
fun QuickSpecsScreen(viewModel: GarageViewModel = hiltViewModel()) {
    val facts by viewModel.facts.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<FactEntity?>(null) }

    ListDetailPane(
        list = {
            Column {
                SectionHeading("Specifications (${facts.size})")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(facts, key = { it.key }) { fact ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selected = fact }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(fact.label, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                valueWithUnit(fact),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        detail = {
            val fact = selected
            if (fact == null) {
                EmptyDetail("Pick a specification. Every value here cites a handbook page.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(fact.label, style = MaterialTheme.typography.titleLarge)
                    Text(valueWithUnit(fact), style = MaterialTheme.typography.displaySmall)
                    ProvenanceBadge(Provenance.fromSource(fact.source), pageRef = fact.pageRef)

                    if (fact.isSafetyCritical) {
                        val citable = SafetyRule.isCitable(
                            Provenance.fromSource(fact.source),
                            fact.pageRef,
                            isSafetyCritical = true,
                        )
                        Text(
                            if (citable) {
                                "Safety-critical. Cited to handbook p.${fact.pageRef}." +
                                    if (fact.verifiedOn == null) " Not yet checked against the page by you." else ""
                            } else {
                                SafetyRule.REFUSAL
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }

                    fact.notes?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
    )
}

private fun valueWithUnit(fact: FactEntity): String =
    if (fact.unit.isNullOrBlank()) fact.value else "${fact.value} ${fact.unit}"
