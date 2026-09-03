package dev.pranav.speed400garage.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import dev.pranav.speed400garage.data.db.entity.EventEntity
import dev.pranav.speed400garage.ui.EmptyDetail
import dev.pranav.speed400garage.ui.ListDetailPane
import dev.pranav.speed400garage.ui.log.Fmt

/**
 * Search across everything, including your own notes (§7.7).
 *
 * This is what makes "has this happened before?" answerable, which is most of the
 * historical value of keeping the log at all.
 */
@Composable
fun SearchScreen(viewModel: SearchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<EventEntity?>(null) }

    ListDetailPane(
        list = {
            Column {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search everything") },
                    placeholder = { Text("rattle, brake pads, Bosch, that workshop in Mysuru…") },
                )
                Spacer(Modifier.height(12.dp))

                when {
                    state.query.isBlank() -> Text(
                        "Searches titles and your own notes across every entry — services, " +
                            "fills, niggles, documents.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.searching -> Text("Searching…", style = MaterialTheme.typography.bodyMedium)
                    state.results.isEmpty() -> Text(
                        "Nothing matches \"${state.query}\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> Text(
                        "${state.results.size} match${if (state.results.size == 1) "" else "es"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(state.results, key = { it.id }) { event ->
                        Column(
                            Modifier.fillMaxWidth().clickable { selected = event }.padding(vertical = 8.dp)
                        ) {
                            Text(event.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                buildString {
                                    append(Fmt.shortDate(event.occurredAt))
                                    event.odometerKm?.let { append(" · ${Fmt.km(it)}") }
                                    append(" · ${event.type}")
                                },
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
            val event = selected
            if (event == null) {
                EmptyDetail("Pick a result to read it.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(event.title, style = MaterialTheme.typography.displaySmall)
                    Text(Fmt.dateMillis(event.occurredAt), style = MaterialTheme.typography.bodyLarge)
                    event.odometerKm?.let { Text("Odometer ${Fmt.km(it)}", style = MaterialTheme.typography.bodyLarge) }
                    event.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        },
    )
}
