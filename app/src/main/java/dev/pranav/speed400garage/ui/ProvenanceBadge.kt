package dev.pranav.speed400garage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.pranav.speed400garage.domain.Provenance

/**
 * Plan §3 P4 — every number the app shows has a source, and the source is visible.
 *
 * [pageRef] is appended for 🟢 values so a figure can be checked against the handbook
 * without leaving the screen.
 */
@Composable
fun ProvenanceBadge(
    provenance: Provenance,
    pageRef: Int? = null,
    modifier: Modifier = Modifier,
) {
    Text(
        text = badgeText(provenance, pageRef),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

internal fun badgeText(provenance: Provenance, pageRef: Int?): String = buildString {
    append(provenance.badge)
    append(' ')
    append(provenance.label)
    if (provenance == Provenance.MANUAL && pageRef != null) append(" · p.$pageRef")
}
