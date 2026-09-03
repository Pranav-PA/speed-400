package dev.pranav.speed400garage.ui.due

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
import dev.pranav.speed400garage.data.db.entity.FactEntity
import dev.pranav.speed400garage.domain.Provenance
import dev.pranav.speed400garage.ui.GarageViewModel
import dev.pranav.speed400garage.ui.ProvenanceBadge

/**
 * The build sheet (§7.10) and the tyre panel (§5.6).
 *
 * Tyres get their own section because they are the one component where age matters as
 * much as wear, where front and rear wear at different rates and get replaced at
 * different times, and where getting it wrong ends in a crash. Every number here is
 * the handbook's, with its page.
 */
@Composable
fun BuildSheetScreen(catalogue: GarageViewModel = hiltViewModel()) {
    val facts by catalogue.facts.collectAsStateWithLifecycle()
    val byKey = facts.associateBy { it.key }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Build sheet", style = MaterialTheme.typography.displaySmall)

        Section("Tyres", "The one place age matters as much as wear — and the one place getting it wrong ends badly.") {
            listOf(
                "tyre.front_size", "tyre.front_pressure",
                "tyre.rear_size", "tyre.rear_pressure",
                "tyre.min_tread_under_130", "tyre.min_tread_over_130_rear",
                "tyre.bedding_in",
            ).forEach { key -> byKey[key]?.let { FactRow(it) } }
            Text(
                "Pressures are cold pressures, checked daily (handbook p.158). This model has " +
                    "one figure per wheel — there is no separate pillion pressure, so the app " +
                    "does not offer one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Section("Engine and fluids", "What goes in it.") {
            listOf(
                "engine.displacement", "engine.configuration", "engine.compression_ratio",
                "oil.grade", "oil.capacity_wet_with_filter",
                "coolant.type", "coolant.capacity",
                "brake.fluid_spec", "fuel.type", "fuel.tank_capacity",
                "spark_plug.type", "spark_plug.gap",
            ).forEach { key -> byKey[key]?.let { FactRow(it) } }
        }

        Section("Drivetrain", "Chain, sprockets and the numbers that keep them alive.") {
            listOf(
                "chain.spec", "chain.links", "chain.free_movement",
                "chain.lube_interval", "chain.lube_spec",
                "transmission.final_drive_ratio", "clutch.lever_free_play",
            ).forEach { key -> byKey[key]?.let { FactRow(it) } }
        }

        Section("Brakes", "Minimum thicknesses, below which the pads are done.") {
            listOf(
                "brake.front_pad_min_lining", "brake.front_min_service_thickness",
                "brake.rear_pad_min_lining", "brake.rear_min_service_thickness",
            ).forEach { key -> byKey[key]?.let { FactRow(it) } }
        }

        Section("Torque", "Eleven figures the handbook gives. Anything not listed here, the app will not guess.") {
            facts.filter { it.category == "torque" }.forEach { FactRow(it) }
        }

        Section("Electrical and load", null) {
            listOf(
                "electrical.battery_type", "electrical.battery_rating",
                "electrical.alternator_rating", "payload.max",
            ).forEach { key -> byKey[key]?.let { FactRow(it) } }
        }
    }
}

@Composable
private fun Section(title: String, subtitle: String?, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
private fun FactRow(fact: FactEntity) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(fact.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            if (fact.unit.isNullOrBlank()) fact.value else "${fact.value} ${fact.unit}",
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        ProvenanceBadge(Provenance.fromSource(fact.source), fact.pageRef)
    }
}
