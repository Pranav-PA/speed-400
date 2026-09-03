package dev.pranav.speed400garage.data.db.entity

import androidx.room.Entity
import androidx.room.Fts4

/**
 * Full-text search over everything, including notes (§7.7, Phase 3).
 *
 * A contentEntity-backed FTS table: SQLite keeps this in step with `event`
 * automatically, so there is no second copy of the truth to drift. Searching a
 * ten-year log is exactly the case where a stale index would be worse than none.
 *
 * FTS4 rather than FTS5 because Room's `@Fts4` has first-class support and ships in
 * every Android SQLite; the gain from FTS5 here would be ranking niceties over a few
 * thousand rows.
 */
@Fts4(contentEntity = EventEntity::class)
@Entity(tableName = "event_fts")
data class EventFts(
    val title: String,
    val notes: String?,
)
