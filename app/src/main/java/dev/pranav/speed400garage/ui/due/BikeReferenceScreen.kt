package dev.pranav.speed400garage.ui.due

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pranav.speed400garage.ui.MaintenanceScreen
import dev.pranav.speed400garage.ui.QuickSpecsScreen

/**
 * Everything the handbook says about the bike, in one place.
 *
 * Previously three separate tabs — Maintenance, Quick Specs, Build sheet. They are all
 * the same kind of thing (reference material, read rarely, never edited) and three
 * top-level entries for one job made the rail long enough to stop being scannable.
 */
@Composable
fun BikeReferenceScreen() {
    var pane by remember { mutableStateOf(Pane.Intervals) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pane.entries.forEach { p ->
                FilterChip(
                    selected = pane == p,
                    onClick = { pane = p },
                    label = { Text(p.title) },
                )
            }
        }
        Column(Modifier.fillMaxWidth().weight(1f)) {
            when (pane) {
                Pane.Intervals -> MaintenanceScreen()
                Pane.Specs -> QuickSpecsScreen()
                Pane.BuildSheet -> BuildSheetScreen()
            }
        }
    }
}

private enum class Pane(val title: String) {
    Intervals("Service intervals"),
    Specs("Quick specs"),
    BuildSheet("Build sheet"),
}
