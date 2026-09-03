package dev.pranav.speed400garage.ui.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
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
import dev.pranav.speed400garage.ui.search.SearchViewModel
import dev.pranav.speed400garage.data.db.entity.EventEntity
import dev.pranav.speed400garage.ui.EmptyDetail
import dev.pranav.speed400garage.ui.ListDetailPane
import dev.pranav.speed400garage.ui.SectionHeading

/**
 * The timeline IS the event log (§6.2).
 *
 * It is a straight read of the `event` table rather than a separate projection, which
 * is why it cannot drift out of sync with the spend totals or the maintenance state —
 * it is the same rows those are computed from.
 */
@Composable
fun TimelineScreen(
    snapshot: GarageSnapshot,
    search: SearchViewModel = hiltViewModel(),
) {
    var filter by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<EventEntity?>(null) }
    val searchState by search.state.collectAsStateWithLifecycle()

    // Search used to be its own tab, which never made sense: searching the log and
    // reading the log are the same activity, and a separate destination just meant
    // deciding which one you wanted before you started.
    val searching = searchState.query.isNotBlank()
    val shown = remember(snapshot.events, filter, searchState.results, searching) {
        when {
            searching -> searchState.results
            filter == null -> snapshot.events
            else -> snapshot.events.filter { it.type == filter }
        }
    }

    ListDetailPane(
        list = {
            Column {
                SectionHeading(if (searching) "Matches (${shown.size})" else "Timeline (${shown.size})")
                OutlinedTextField(
                    value = searchState.query,
                    onValueChange = search::onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search titles and notes") },
                    placeholder = { Text("rattle, brake pads, that workshop…") },
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(filter == null, { filter = null }, { Text("All") })
                    FilterChip(filter == "fuel", { filter = "fuel" }, { Text("Fuel") })
                    FilterChip(filter == "service", { filter = "service" }, { Text("Service") })
                    FilterChip(filter == "part", { filter = "part" }, { Text("Spend") })
                }
                Spacer(Modifier.height(12.dp))

                if (shown.isEmpty()) {
                    Text(
                        "Nothing here yet. Everything you log — a fill, an expense, a service, " +
                            "a reading — lands on this one list.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(shown, key = { it.id }) { event ->
                            Row(
                                Modifier.fillMaxWidth().clickable { selected = event }.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(iconFor(event.type), style = MaterialTheme.typography.titleLarge)
                                Column(Modifier.weight(1f)) {
                                    Text(event.title, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        buildString {
                                            append(Fmt.shortDate(event.occurredAt))
                                            event.odometerKm?.let { append(" · ${Fmt.km(it)}") }
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        detail = {
            val event = selected
            if (event == null) {
                EmptyDetail("Pick an entry to see what it recorded.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(event.title, style = MaterialTheme.typography.displaySmall)
                    Text(Fmt.dateMillis(event.occurredAt), style = MaterialTheme.typography.bodyLarge)
                    event.odometerKm?.let {
                        Text("Odometer ${Fmt.km(it)}", style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(
                        "Type: ${event.type}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    event.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        },
    )
}

private fun iconFor(type: String) = when (type) {
    "fuel" -> "⛽"
    "service" -> "🔧"
    "repair" -> "🛠"
    "part", "accessory" -> "📦"
    "document" -> "📄"
    "odo_reading" -> "🔢"
    "fault" -> "⚠"
    "purchase" -> "🏍"
    else -> "•"
}
