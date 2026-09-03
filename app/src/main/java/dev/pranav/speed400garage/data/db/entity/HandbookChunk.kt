package dev.pranav.speed400garage.data.db.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A passage of the owner's handbook, with the page it came from (§10.2).
 *
 * The page reference is the whole point. A retrieved passage without one is an
 * unverifiable claim; with one, every 🟢 answer can be checked against the printed
 * page in seconds. §3 P4 is not satisfied by "the manual says so" — it is satisfied by
 * "the manual says so, on page 134".
 */
@Entity(tableName = "handbook_chunk", indices = [Index("page")])
data class HandbookChunkEntity(
    @PrimaryKey val id: String = newId(),
    /** Printed page number, which in this handbook equals the PDF page index. */
    val page: Int,
    /** Position within the page, for pages split into more than one passage. */
    val ordinal: Int,
    val text: String,
    /** The section heading this passage sits under, when one could be identified. */
    val section: String?,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
)

@Fts4(contentEntity = HandbookChunkEntity::class)
@Entity(tableName = "handbook_chunk_fts")
data class HandbookChunkFts(
    val text: String,
    val section: String?,
)
