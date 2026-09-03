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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import dev.pranav.speed400garage.ui.chart.AnalyticsScreen
import dev.pranav.speed400garage.ui.due.DocumentSheet
import dev.pranav.speed400garage.ui.due.DueScreen
import dev.pranav.speed400garage.ui.due.FaultSheet
import dev.pranav.speed400garage.ui.due.BikeReferenceScreen
import dev.pranav.speed400garage.ui.assistant.AssistantScreen
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
 * The app shell, adaptive.
 *
 * This reverses plan §4.1's tablet-only decision, deliberately and for a reason: §4.1
 * chose one device and invented the Capture Inbox to bridge the gap between where data
 * is created (roadside) and where it was entered (at home). With the Inbox cut, that
 * bridge is gone, so the phone has to be a real client rather than a camera feeding one.
 *
 * What replaces it is not a compromise layout. Wide screens keep exactly what they had
 * — a permanent rail and two panes side by side. Narrow screens get a bottom bar and
 * one pane at a time, which is what a phone actually wants. The branch happens in two
 * places only ([GarageApp] and [ListDetailPane]), so no individual screen has to know
 * how wide it is.
 */
@Composable
fun GarageApp() {
    var destination by remember { mutableStateOf(Destination.Dashboard) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }

    NotificationPermissionRequest()

    val updateViewModel: UpdateViewModel = hiltViewModel()
    LaunchedEffect(Unit) { updateViewModel.checkOnLaunch() }
    UpdatePrompt(updateViewModel)

    val stateViewModel: GarageStateViewModel = hiltViewModel()
    val snapshot by stateViewModel.snapshot.collectAsStateWithLifecycle()

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val compact = maxWidth < COMPACT_WIDTH_DP.dp

        CompositionLocalProvider(LocalIsCompact provides compact) {
            AdaptiveChrome(
                compact = compact,
                destination = destination,
                onDestination = { destination = it },
            ) {
                when (destination) {
                Destination.Dashboard -> DashboardV1(
                    snapshot = snapshot,
                    onLogFuel = { sheet = Sheet.Fuel },
                    onLogExpense = { sheet = Sheet.Expense },
                    onLogOdometer = { sheet = Sheet.Odometer },
                    onLogService = { sheet = Sheet.Service },
                    onLogDocument = { sheet = Sheet.Document },
                    onLogFault = { sheet = Sheet.Fault },
                )
                Destination.Due -> DueScreen()
                Destination.Assistant -> AssistantScreen()
                Destination.Timeline -> TimelineScreen(snapshot)
                Destination.Analytics -> AnalyticsScreen()
                Destination.Bike -> BikeReferenceScreen()
                    Destination.Settings -> SettingsScreen()
                }
            }

            sheet?.let { LogSheetHost(it) { sheet = null } }
        }
    }
}

/**
 * Rail beside the content on a wide screen, bottom bar under it on a narrow one.
 *
 * A rail on a phone would eat a quarter of the width; a bottom bar on a tablet wastes
 * the vertical space that makes a tablet worth using. Neither is a fallback for the
 * other — they are the right answer for their own shape.
 */
@Composable
private fun AdaptiveChrome(
    compact: Boolean,
    destination: Destination,
    onDestination: (Destination) -> Unit,
    content: @Composable () -> Unit,
) {
    if (compact) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
                content()
            }
            NavigationBar {
                Destination.entries.forEach { d ->
                    NavigationBarItem(
                        selected = destination == d,
                        onClick = { onDestination(d) },
                        icon = { Icon(d.icon, contentDescription = d.title) },
                        // Labels only on the selected item: seven labels across a phone
                        // truncate to noise, and the icon plus the selected label is
                        // enough to know where you are.
                        label = { Text(d.title) },
                        alwaysShowLabel = false,
                    )
                }
            }
        }
    } else {
        Row(Modifier.fillMaxSize()) {
            NavigationRail {
                Destination.entries.forEach { d ->
                    NavigationRailItem(
                        selected = destination == d,
                        onClick = { onDestination(d) },
                        icon = { Icon(d.icon, contentDescription = d.title) },
                        label = { Text(d.title) },
                    )
                }
            }
            Box(Modifier.fillMaxSize().padding(24.dp)) { content() }
        }
    }
}

enum class Sheet { Fuel, Expense, Odometer, Service, Document, Fault }

/**
 * Asks for the notification permission once, on first launch.
 *
 * Without it the reminder engine still computes correctly and the Due screen still
 * works — it simply cannot tell you anything you did not ask to see, which defeats
 * most of §8.
 */
@Composable
private fun NotificationPermissionRequest() {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
}

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
                    Sheet.Document -> "Add a document"
                    Sheet.Fault -> "Log a niggle"
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
                    Sheet.Document -> DocumentSheet { type, issuer, number, expires, secondary, amount, notes ->
                        vm.saveDocument(now, type, issuer, number, expires, secondary, amount, notes)
                    }
                    Sheet.Fault -> FaultSheet(lastOdo) { summary, odo, notes ->
                        vm.saveFault(now, odo, summary, notes)
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

/**
 * Seven destinations, down from eleven.
 *
 * The rail grew a tab per phase until it stopped being scannable, which is its own
 * kind of bug — an app you have to search for the right tab in is not a fast app.
 * Nothing was removed: readiness moved onto the dashboard because "can I ride
 * tomorrow" is a home-screen question, search moved into the timeline because
 * searching the log and reading it are one activity, and the three reference screens
 * became panes of one Bike screen because they are all the same kind of thing.
 */
enum class Destination(val title: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Filled.Dashboard),
    Due("Due", Icons.Filled.NotificationsActive),
    Assistant("Ask", Icons.AutoMirrored.Filled.Chat),
    Timeline("Timeline", Icons.Filled.History),
    Analytics("Analytics", Icons.Filled.InsertChart),
    Bike("Bike", Icons.AutoMirrored.Filled.MenuBook),
    Settings("Settings", Icons.Filled.Settings),
}

/**
 * List and detail, side by side where there is room and one at a time where there
 * isn't.
 *
 * On a wide screen both panes are always present — there is no reason to hide one, and
 * a detail that appears beside the list you picked from is genuinely better.
 *
 * On a narrow screen the list fills the screen until something is selected, then the
 * detail takes over with a back affordance and the system back button wired up. The
 * alternative — squeezing both into a phone width — gives you two unusable columns
 * instead of one good one.
 *
 * @param hasSelection whether [detail] currently has something to show. Callers own
 *   their selection state, so they have to say; a pane that guessed would get it wrong.
 * @param onBack clears that selection.
 */
@Composable
fun ListDetailPane(
    hasSelection: Boolean = true,
    onBack: () -> Unit = {},
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit,
) {
    if (LocalIsCompact.current) {
        if (hasSelection) {
            BackHandler(onBack = onBack)
            Column(Modifier.fillMaxSize()) {
                TextButton(onClick = onBack) { Text("‹  Back") }
                Surface(
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                    color = MaterialTheme.colorScheme.surface,
                ) { Box(Modifier.padding(16.dp)) { detail() } }
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                color = MaterialTheme.colorScheme.surface,
            ) { Box(Modifier.padding(12.dp)) { list() } }
        }
        return
    }

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
