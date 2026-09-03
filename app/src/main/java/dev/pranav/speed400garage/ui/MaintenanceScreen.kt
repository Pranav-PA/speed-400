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
import dev.pranav.speed400garage.data.db.entity.ComponentEntity
import dev.pranav.speed400garage.domain.Provenance

/**
 * The component catalogue as seeded from the handbook.
 *
 * The point of this screen in Phase 0 is auditability: each row shows its interval AND
 * where the interval came from, so the Phase 0 done-when criterion can be checked by
 * eye rather than taken on trust.
 */
@Composable
fun MaintenanceScreen(viewModel: GarageViewModel = hiltViewModel()) {
    val components by viewModel.components.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<ComponentEntity?>(null) }

    ListDetailPane(
        hasSelection = selected != null,
        onBack = { selected = null },
        list = {
            Column {
                SectionHeading("Components (${components.size})")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(components, key = { it.key }) { component ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selected = component }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(component.displayName, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                intervalSummary(component),
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
            val component = selected
            if (component == null) {
                EmptyDetail("Pick a component to see its interval and where it came from.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(component.displayName, style = MaterialTheme.typography.displaySmall)
                    ProvenanceBadge(
                        Provenance.fromSource(component.intervalSource),
                        pageRef = component.manualPageRef,
                    )
                    Text(intervalSummary(component), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Action: ${component.actionKind}" +
                            (if (component.isWarrantyRelevant) " · warranty-relevant" else ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    component.notes?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
    )
}

/**
 * Renders an interval in words. A component with neither a km nor a day interval is
 * condition-based in the handbook — it says so rather than implying a schedule exists.
 */
internal fun intervalSummary(component: ComponentEntity): String {
    val km = component.intervalKm
    val days = component.intervalDays
    return when {
        component.isDailyCheck -> "Every ride (daily check)"
        km != null && days != null -> "Every ${"%,d".format(km)} km or ${dayPhrase(days)}, whichever comes first"
        km != null && component.isOneOff -> "Once, at ${"%,d".format(km)} km"
        km != null -> "Every ${"%,d".format(km)} km"
        days != null -> "Every ${dayPhrase(days)}"
        else -> "Condition-based — the handbook gives no interval"
    }
}

private fun dayPhrase(days: Int): String = when {
    days % 365 == 0 || days == 366 -> "${days / 365} year".pluralise(days / 365)
    days in 1460..1461 -> "4 years"
    days in 730..731 -> "2 years"
    days in 180..186 -> "6 months"
    days == 1 -> "day"
    else -> "$days days"
}

private fun String.pluralise(n: Int) = if (n == 1) this else this + "s"
