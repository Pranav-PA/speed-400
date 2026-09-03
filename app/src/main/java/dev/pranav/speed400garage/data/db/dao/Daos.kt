package dev.pranav.speed400garage.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.pranav.speed400garage.data.db.entity.AttachmentEntity
import dev.pranav.speed400garage.data.db.entity.BikeEntity
import dev.pranav.speed400garage.data.db.entity.CaptureInboxEntity
import dev.pranav.speed400garage.data.db.entity.ComponentActionEntity
import dev.pranav.speed400garage.data.db.entity.ComponentEntity
import dev.pranav.speed400garage.data.db.entity.DocumentEntity
import dev.pranav.speed400garage.data.db.entity.EventEntity
import dev.pranav.speed400garage.data.db.entity.FactEntity
import dev.pranav.speed400garage.data.db.entity.FaultEntity
import dev.pranav.speed400garage.data.db.entity.FuelEntryEntity
import dev.pranav.speed400garage.data.db.entity.LineItemEntity
import dev.pranav.speed400garage.data.db.entity.OdometerReadingEntity
import dev.pranav.speed400garage.data.db.entity.SettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BikeDao {
    @Upsert suspend fun upsert(bike: BikeEntity)

    @Query("SELECT * FROM bike WHERE isActive = 1 ORDER BY createdAt LIMIT 1")
    fun observeActiveBike(): Flow<BikeEntity?>

    @Query("SELECT * FROM bike WHERE isActive = 1 ORDER BY createdAt LIMIT 1")
    suspend fun activeBike(): BikeEntity?

    @Query("SELECT COUNT(*) FROM bike") suspend fun count(): Int
}

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(event: EventEntity)

    /** The Timeline IS this query — it cannot drift out of sync with the other screens (§6.2). */
    @Query("SELECT * FROM event WHERE bikeId = :bikeId ORDER BY occurredAt DESC, createdAt DESC")
    fun observeTimeline(bikeId: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM event WHERE bikeId = :bikeId ORDER BY occurredAt DESC, createdAt DESC LIMIT :limit")
    fun observeRecent(bikeId: String, limit: Int): Flow<List<EventEntity>>

    @Query("SELECT COUNT(*) FROM event WHERE bikeId = :bikeId") fun observeCount(bikeId: String): Flow<Int>
}

@Dao
interface LineItemDao {
    @Insert suspend fun insertAll(items: List<LineItemEntity>)

    /**
     * Every money figure in the app comes through a query shaped like this one —
     * a SUM over line items under a filter, never a sum of events (§4.2 invariant).
     */
    @Query(
        """
        SELECT COALESCE(SUM(li.amountPaise), 0) FROM line_item li
        JOIN event e ON e.id = li.eventId
        WHERE e.bikeId = :bikeId AND e.occurredAt BETWEEN :fromMillis AND :toMillis
        """
    )
    suspend fun totalPaiseBetween(bikeId: String, fromMillis: Long, toMillis: Long): Long

    @Query(
        """
        SELECT COALESCE(SUM(li.amountPaise), 0) FROM line_item li
        JOIN event e ON e.id = li.eventId
        WHERE e.bikeId = :bikeId AND li.category = :category
        """
    )
    suspend fun totalPaiseByCategory(bikeId: String, category: String): Long
}

@Dao
interface OdometerDao {
    @Insert suspend fun insert(reading: OdometerReadingEntity)

    /** Observed readings only, newest first. Projections are never stored here (§5.1). */
    @Query("SELECT * FROM odometer_reading WHERE bikeId = :bikeId ORDER BY readAt DESC")
    fun observeReadings(bikeId: String): Flow<List<OdometerReadingEntity>>

    @Query("SELECT * FROM odometer_reading WHERE bikeId = :bikeId ORDER BY readAt DESC LIMIT :limit")
    suspend fun recentReadings(bikeId: String, limit: Int): List<OdometerReadingEntity>

    @Query("SELECT * FROM odometer_reading WHERE bikeId = :bikeId ORDER BY readAt DESC LIMIT 1")
    fun observeLatest(bikeId: String): Flow<OdometerReadingEntity?>
}

@Dao
interface ComponentDao {
    @Upsert suspend fun upsertAll(components: List<ComponentEntity>)

    @Query("SELECT * FROM component ORDER BY isDailyCheck, displayName")
    fun observeAll(): Flow<List<ComponentEntity>>

    @Query("SELECT * FROM component WHERE `key` = :key LIMIT 1")
    suspend fun byKey(key: String): ComponentEntity?

    /** One-shot read for the background recompute, which has no lifecycle to observe with. */
    @Query("SELECT * FROM component ORDER BY isDailyCheck, displayName")
    suspend fun allOnce(): List<ComponentEntity>

    @Query("SELECT COUNT(*) FROM component") suspend fun count(): Int

    /**
     * Phase 0's done-when criterion, as a query: every interval in the app traces to a
     * page number in the handbook. Anything returned here does NOT, and must render ⚪.
     */
    @Query("SELECT * FROM component WHERE intervalSource = 'manual' AND manualPageRef IS NULL")
    suspend fun manualComponentsMissingPageRef(): List<ComponentEntity>

    @Query("SELECT * FROM component WHERE intervalSource != 'manual'")
    suspend fun unverifiedComponents(): List<ComponentEntity>
}

@Dao
interface ComponentActionDao {
    @Insert suspend fun insertAll(actions: List<ComponentActionEntity>)

    /** The last time each component was acted on — what an interval resets against. */
    @Query(
        """
        SELECT ca.componentKey AS componentKey,
               MAX(e.occurredAt) AS lastActionAt,
               MAX(e.odometerKm) AS lastActionOdometerKm
        FROM component_action ca
        JOIN event e ON e.id = ca.eventId
        WHERE e.bikeId = :bikeId AND ca.action IN ('replaced', 'serviced', 'adjusted')
        GROUP BY ca.componentKey
        """
    )
    suspend fun lastActions(bikeId: String): List<ComponentLastAction>
}

data class ComponentLastAction(
    val componentKey: String,
    val lastActionAt: Long?,
    val lastActionOdometerKm: Int?,
)

@Dao
interface FactDao {
    @Upsert suspend fun upsertAll(facts: List<FactEntity>)

    @Query("SELECT * FROM fact ORDER BY category, label") fun observeAll(): Flow<List<FactEntity>>

    @Query("SELECT * FROM fact WHERE `key` = :key LIMIT 1") suspend fun byKey(key: String): FactEntity?

    @Query("SELECT COUNT(*) FROM fact") suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM fact WHERE verifiedOn IS NOT NULL") fun observeVerifiedCount(): Flow<Int>

    /** §10.5 — a safety-critical fact with no page citation is not answerable. */
    @Query("SELECT * FROM fact WHERE isSafetyCritical = 1 AND (source != 'manual' OR pageRef IS NULL)")
    suspend fun uncitableSafetyFacts(): List<FactEntity>
}

@Dao
interface DocumentDao {
    @Insert suspend fun insert(document: DocumentEntity)

    @Query("SELECT * FROM document ORDER BY expiresOn") fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM document ORDER BY expiresOn") suspend fun all(): List<DocumentEntity>
}

@Dao
interface FaultDao {
    @Insert suspend fun insert(fault: FaultEntity)

    /** Open niggles, newest first — this is the list read out at the service counter. */
    @Query(
        """
        SELECT f.* FROM fault f
        JOIN event e ON e.id = f.eventId
        WHERE e.bikeId = :bikeId AND f.status IN ('open', 'watching')
        ORDER BY e.occurredAt DESC
        """
    )
    fun observeOpen(bikeId: String): Flow<List<FaultEntity>>

    @Query(
        """
        SELECT f.* FROM fault f
        JOIN event e ON e.id = f.eventId
        WHERE e.bikeId = :bikeId
        ORDER BY e.occurredAt DESC
        """
    )
    fun observeAll(bikeId: String): Flow<List<FaultEntity>>

    @Query("UPDATE fault SET status = :status, resolvedByEventId = :resolvedBy, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: String, status: String, resolvedBy: String?, now: Long)
}

@Dao
interface AttachmentDao {
    @Insert suspend fun insertAll(attachments: List<AttachmentEntity>)

    @Query("SELECT * FROM attachment WHERE eventId = :eventId") suspend fun forEvent(eventId: String): List<AttachmentEntity>
}

@Dao
interface CaptureInboxDao {
    @Upsert suspend fun upsert(item: CaptureInboxEntity)

    @Query("SELECT * FROM capture_inbox WHERE status IN ('pending', 'ocr_done') ORDER BY importedAt DESC")
    fun observePending(): Flow<List<CaptureInboxEntity>>

    /** The count that sits on the dashboard, because batched things get forgotten (§3 P1). */
    @Query("SELECT COUNT(*) FROM capture_inbox WHERE status IN ('pending', 'ocr_done')")
    fun observePendingCount(): Flow<Int>
}

/** A fuel fill flattened across its event and facet — what the economy engine needs. */
data class FillRow(
    val eventId: String,
    val occurredAt: Long,
    val odometerKm: Int?,
    val litres: Double,
    val pricePerLitrePaise: Long?,
    val amountPaise: Long,
    val fillType: String,
    val missedPrevious: Boolean,
)

@Dao
interface FuelDao {
    @Query(
        """
        SELECT e.id AS eventId, e.occurredAt AS occurredAt, e.odometerKm AS odometerKm,
               f.litres AS litres, f.pricePerLitrePaise AS pricePerLitrePaise,
               f.amountPaise AS amountPaise, f.fillType AS fillType,
               f.missedPrevious AS missedPrevious
        FROM fuel_entry f
        JOIN event e ON e.id = f.eventId
        WHERE e.bikeId = :bikeId
        ORDER BY e.occurredAt ASC, e.createdAt ASC
        """
    )
    suspend fun fillsFor(bikeId: String): List<FillRow>

    @Query(
        """
        SELECT e.id AS eventId, e.occurredAt AS occurredAt, e.odometerKm AS odometerKm,
               f.litres AS litres, f.pricePerLitrePaise AS pricePerLitrePaise,
               f.amountPaise AS amountPaise, f.fillType AS fillType,
               f.missedPrevious AS missedPrevious
        FROM fuel_entry f
        JOIN event e ON e.id = f.eventId
        WHERE e.bikeId = :bikeId
        ORDER BY e.occurredAt ASC, e.createdAt ASC
        """
    )
    fun observeFills(bikeId: String): Flow<List<FillRow>>
}

/** Every line item under a bike, flattened for the cost engine (§4.2). */
data class CostRow(val category: String, val amountPaise: Long, val occurredAt: Long)

@Dao
interface CostDao {
    @Query(
        """
        SELECT li.category AS category, li.amountPaise AS amountPaise, e.occurredAt AS occurredAt
        FROM line_item li
        JOIN event e ON e.id = li.eventId
        WHERE e.bikeId = :bikeId
        ORDER BY e.occurredAt ASC
        """
    )
    fun observeAll(bikeId: String): Flow<List<CostRow>>
}

@Dao
interface SettingDao {
    @Upsert suspend fun put(setting: SettingEntity)

    @Query("SELECT value FROM setting WHERE `key` = :key LIMIT 1") suspend fun get(key: String): String?
}

/** Writes a whole real-world happening as ONE event plus its facets (§3 P2). */
@Dao
interface EventWriteDao {
    @Insert suspend fun insertEvent(event: EventEntity)
    @Insert suspend fun insertLineItems(items: List<LineItemEntity>)
    @Insert suspend fun insertComponentActions(actions: List<ComponentActionEntity>)
    @Insert suspend fun insertAttachments(attachments: List<AttachmentEntity>)
    @Insert suspend fun insertOdometerReading(reading: OdometerReadingEntity)
    @Insert suspend fun insertFuelEntry(entry: FuelEntryEntity)

    @Transaction
    suspend fun writeEvent(
        event: EventEntity,
        lineItems: List<LineItemEntity> = emptyList(),
        componentActions: List<ComponentActionEntity> = emptyList(),
        attachments: List<AttachmentEntity> = emptyList(),
        odometerReading: OdometerReadingEntity? = null,
        fuelEntry: FuelEntryEntity? = null,
    ) {
        insertEvent(event)
        if (lineItems.isNotEmpty()) insertLineItems(lineItems)
        if (componentActions.isNotEmpty()) insertComponentActions(componentActions)
        if (attachments.isNotEmpty()) insertAttachments(attachments)
        odometerReading?.let { insertOdometerReading(it) }
        fuelEntry?.let { insertFuelEntry(it) }
    }
}
