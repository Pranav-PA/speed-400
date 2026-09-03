package dev.pranav.speed400garage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.hilt.navigation.compose.hiltViewModel

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

    // Checked once per launch, and silent unless there is something to act on.
    val updateViewModel: UpdateViewModel = hiltViewModel()
    LaunchedEffect(Unit) { updateViewModel.checkOnLaunch() }
    UpdatePrompt(updateViewModel)

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
                Destination.Dashboard -> DashboardScreen()
                Destination.Maintenance -> MaintenanceScreen()
                Destination.QuickSpecs -> QuickSpecsScreen()
                Destination.Settings -> SettingsScreen()
            }
        }
    }
}

enum class Destination(val title: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Filled.Dashboard),
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
