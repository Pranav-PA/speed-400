package dev.pranav.speed400garage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pranav.speed400garage.ui.log.DashboardV1
import dev.pranav.speed400garage.ui.log.ExpenseSheet
import dev.pranav.speed400garage.ui.log.FuelSheet
import dev.pranav.speed400garage.ui.log.GarageStateViewModel
import dev.pranav.speed400garage.ui.log.LogState
import dev.pranav.speed400garage.ui.log.LogViewModel
import dev.pranav.speed400garage.ui.log.OdometerSheet
import dev.pranav.speed400garage.ui.log.ServiceSheet
import dev.pranav.speed400garage.ui.log.TimelineScreen
import dev.pranav.speed400garage.ui.log.ValidationDialog

/**
 * The app shell.
 *
 * Tablet-only, landscape-first, fixed panes (§4.1). There is deliberately no
 * `WindowSizeClass` anywhere in this codebase: a permanent navigation rail on the left
 * and a two-pane list/detail body is THE layout, not one branch of a responsive one.
 */
@Composable
fun GarageApp() {
    var destination by remember { mutableStateOf(Destination.Dashboard) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }

    val updateViewModel: UpdateViewModel = hiltViewModel()
    LaunchedEffect(Unit) { updateViewModel.checkOnLaunch() }
    UpdatePrompt(updateViewModel)

    val stateViewModel: GarageStateViewModel = hiltViewModel()
    val snapshot by stateViewModel.snapshot.collectAsStateWithLifecycle()

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavigationRail {
            Destination.entries.forEach { d ->
                NavigationRailItem(
                    selected = destination == d,
                    onClick = { destination = d },
                    icon = { Icon(d.icon, contentDescription = d.title) },
                    label = { Text(d.title) },
                )
            }
        }
        Box(Modifier.fillMaxSize().padding(24.dp)) {
            when (destination) {
                Destination.Dashboard -> DashboardV1(
                    snapshot = snapshot,
                    onLogFuel = { sheet = Sheet.Fuel },
                    onLogExpense = { sheet = Sheet.Expense },
                    onLogOdometer = { sheet = Sheet.Odometer },
                    onLogService = { sheet = Sheet.Service },
                )
                Destination.Timeline -> TimelineScreen(snapshot)
                Destination.Maintenance -> MaintenanceScreen()
                Destination.QuickSpecs -> QuickSpecsScreen()
                Destination.Settings -> SettingsScreen()
            }
        }
    }

    sheet?.let { LogSheetHost(it) { sheet = null } }
}

enum class Sheet { Fuel, Expense, Odometer, Service }

/**
 * Hosts an entry sheet and the §9.2 questions it may raise.
 *
 * The questions are shown between "save" and the write, never as a gate on typing:
 * interrupting mid-entry to argue about a number is how capture flows get abandoned.
 */
@Composable
private fun LogSheetHost(sheet: Sheet, onClose: () -> Unit) {
    val vm: LogViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val lastOdo by vm.lastOdometer.collectAsStateWithLifecycle()
    val lastRate by vm.lastRatePaise.collectAsStateWithLifecycle()
    var pendingSave by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(sheet) { vm.reset(); vm.refreshContext() }
    LaunchedEffect(state) { if (state is LogState.Saved) { vm.reset(); onClose() } }

    val now = System.currentTimeMillis()

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(
                when (sheet) {
                    Sheet.Fuel -> "Log a fill"
                    Sheet.Expense -> "Log an expense"
                    Sheet.Odometer -> "Update the odometer"
                    Sheet.Service -> "Log a service or part"
                }
            )
        },
        text = {
            Box(Modifier.fillMaxWidth()) {
                when (sheet) {
                    Sheet.Fuel -> FuelSheet(lastOdo, lastRate) { odo, amount, rate, litres, type, missed, computed, notes ->
                        pendingSave = { vm.saveFuel(odo, amount, rate, litres, type, missed, computed, null, notes, now, confirmed = true) }
                        vm.saveFuel(odo, amount, rate, litres, type, missed, computed, null, notes, now)
                    }
                    Sheet.Expense -> ExpenseSheet(lastOdo) { odo, title, category, amount, notes ->
                        pendingSave = { vm.saveExpense(now, odo, title, category, amount, notes, confirmed = true) }
                        vm.saveExpense(now, odo, title, category, amount, notes)
                    }
                    Sheet.Odometer -> OdometerSheet(lastOdo) { km ->
                        pendingSave = { vm.saveOdometer(now, km, confirmed = true) }
                        vm.saveOdometer(now, km)
                    }
                    Sheet.Service -> ServiceSheet(lastOdo) { odo, title, vendor, lines, components, action, notes ->
                        pendingSave = { vm.saveService(now, odo, title, vendor, lines, components, action, notes, confirmed = true) }
                        vm.saveService(now, odo, title, vendor, lines, components, action, notes)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )

    (state as? LogState.Asking)?.let { asking ->
        ValidationDialog(
            result = asking.result,
            onDismiss = { vm.reset() },
            onConfirm = { vm.reset(); pendingSave?.invoke() },
        )
    }
    (state as? LogState.Error)?.let { error ->
        AlertDialog(
            onDismissRequest = { vm.reset() },
            title = { Text("Could not save") },
            text = { Text(error.message) },
            confirmButton = { TextButton(onClick = { vm.reset() }) { Text("OK") } },
        )
    }
}

enum class Destination(val title: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Filled.Dashboard),
    Timeline("Timeline", Icons.Filled.History),
    Maintenance("Maintenance", Icons.Filled.Build),
    QuickSpecs("Quick Specs", Icons.AutoMirrored.Filled.MenuBook),
    Settings("Settings", Icons.Filled.Settings),
}

/**
 * The fixed two-pane body every screen in this app uses: a list on the left, the
 * selected item's detail on the right. Both panes are always present — on a tablet in
 * landscape there is no reason to hide one, and hiding one is what forces the
 * responsive branching §4.1 rules out.
 */
@Composable
fun ListDetailPane(
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Surface(
            modifier = Modifier.width(400.dp).fillMaxHeight().clip(RoundedCornerShape(12.dp)),
            color = MaterialTheme.colorScheme.surface,
        ) { Box(Modifier.padding(16.dp)) { list() } }

        Surface(
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
            color = MaterialTheme.colorScheme.surface,
        ) { Box(Modifier.padding(24.dp)) { detail() } }
    }
}

@Composable
fun EmptyDetail(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionHeading(text: String) {
    Column {
        Text(text, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
    }
}
