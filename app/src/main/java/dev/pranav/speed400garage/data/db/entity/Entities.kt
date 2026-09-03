package dev.pranav.speed400garage.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * The plan's §6 spine, as Room entities.
 *
 * Two conventions hold across every table here, both from the plan:
 *  - UUID string primary keys, so a row minted on a restored device can never collide
 *    with one minted before the restore (§6, P6).
 *  - a row-level [updatedAt] epoch-millis stamp, so an export/restore or a future sync
 *    can reason about which copy of a row is newer.
 */
fun newId(): String = UUID.randomUUID().toString()

fun now(): Long = System.currentTimeMillis()

// ---------------------------------------------------------------- bike

@Entity(tableName = "bike")
data class BikeEntity(
    @PrimaryKey val id: String = newId(),
    val make: String,
    val model: String,
    val modelYear: Int?,
    val registration: String?,
    val vin: String?,
    val engineNumber: String?,
    val colour: String?,
    /** Epoch day the bike was bought — the anchor for "days owned" and warranty. */
    val purchasedOn: Long?,
    val purchasePricePaise: Long?,
    val photoUri: String?,
    /** The handbook part number this bike's 🟢 facts are cited against. */
    val handbookPartNumber: String?,
    val isActive: Boolean = true,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
)

// ---------------------------------------------------------------- event (the spine)

/**
 * Plan §3 P2 — one append-only event log, many views. A service visit is ONE of these
 * carrying several [LineItemEntity] rows and several [ComponentActionEntity] rows, not
 * seven separate records.
 */
@Entity(
    tableName = "event",
    foreignKeys = [
        ForeignKey(BikeEntity::class, ["id"], ["bikeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(VendorEntity::class, ["id"], ["vendorId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("bikeId"), Index("vendorId"), Index("occurredAt"), Index("type")],
)
data class EventEntity(
    @PrimaryKey val id: String = newId(),
    val bikeId: String,
    /** fuel · service · repair · part · accessory · document · ride · fault · note · odo_reading · purchase */
    val type: String,
    /** Epoch millis. The date is required; the time-of-day component may be meaningless. */
    val occurredAt: Long,
    val hasTimeOfDay: Boolean = false,
    /**
     * Plan §3 P3 — the odometer is the spine. Nullable, but prompted for on every type
     * that plausibly has one. This column only ever holds an OBSERVED reading; the
     * app's projection of today's odometer is derived at read time and never written
     * here (§5.1).
     */
    val odometerKm: Int?,
    val title: String,
    val notes: String?,
    val vendorId: String?,
    val location: String?,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
)

/**
 * Plan §4.2 — money exists in exactly one place. No total in this app is ever computed
 * by summing events; every figure is a SUM over these rows, which makes double-counting
 * structurally impossible.
 *
 * Amounts are integer paise, never floating point.
 */
@Entity(
    tableName = "line_item",
    foreignKeys = [ForeignKey(EventEntity::class, ["id"], ["eventId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("eventId"), Index("category")],
)
data class LineItemEntity(
    @PrimaryKey val id: String = newId(),
    val eventId: String,
    /** fuel · labour · parts · consumables · accessories · insurance · puc · rto · washing · parking · tolls · gear · other */
    val category: String,
    val description: String?,
    val qty: Double?,
    val unitPricePaise: Long?,
    val amountPaise: Long,
    val isEstimate: Boolean = false,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
)

/**
 * A facet of a `fuel` event, not a separate thing (plan §4.2).
 *
 * Entry is ₹-first (§7.2): amount and rate go in, litres are derived — which is the
 * way an Indian pump actually works. [isComputedLitres] records that derivation so the
 * economy engine knows the litres figure is arithmetic, not a reading.
 */
@Entity(
    tableName = "fuel_entry",
    foreignKeys = [ForeignKey(EventEntity::class, ["id"], ["eventId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["eventId"], unique = true)],
)
data class FuelEntryEntity(
    @PrimaryKey val id: String = newId(),
    val eventId: String,
    val litres: Double,
    val pricePerLitrePaise: Long?,
    val amountPaise: Long,
    /** full · partial · first */
    val fillType: String,
    /**
     * Set when a fill is known to have gone unrecorded before this one. It BREAKS the
     * full-to-full chain rather than producing a wrong economy number (§9.1).
     */
    val missedPrevious: Boolean = false,
    val stationId: String?,
    val fuelGrade: String?,
    val isComputedLitres: Boolean = true,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
)

// ---------------------------------------------------------------- odometer

/**
 * An observed odometer reading (§5.1). Every row here is something a human read off the
 * cluster or the app read off a photograph — never an estimate. Estimates are computed
 * on demand and are not persisted, so a projection can never later be mistaken for an
 * observation.
 */
@Entity(
    tableName = "odometer_reading",
    foreignKeys = [
        ForeignKey(BikeEntity::class, ["id"], ["bikeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(EventEntity::class, ["id"], ["eventId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("bikeId"), Index("eventId"), Index("readAt")],
)
data class OdometerReadingEntity(
    @PrimaryKey val id: String = newId(),
    val bikeId: String,
    /** The event this reading was captured alongside, when there was one. */
    val eventId: String?,
    val readAt: Long,
    val odometerKm: Int,
    /** manual · ocr · imported */
    val captureMethod: String = "manual",
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
)

// ---------------------------------------------------------------- components

/**
 * The component catalogue, seeded from the handbook in Phase 0.
 *
 * [intervalSource] and [manualPageRef] together are what let the UI render a 🟢 badge.
 * A component whose source is `unverified` renders ⚪ and must not be presented as a
 * manufacturer interval — see the five such rows in `components.json`, all of which are
 * condition-based in the handbook rather than interval-based.
 */
@Entity(tableName = "component", indices = [Index(value = ["key"], unique = true)])
data class ComponentEntity(
    @PrimaryKey val id: String = newId(),
    val key: String,
    val displayName: String,
    /** replace · service · check · adjust */
    val actionKind: String,
    val intervalKm: Int?,
    val intervalDays: Int?,
    /** manual · dealer · community · mine · unverified */
    val intervalSource: String,
    val manualPageRef: Int?,
    val isWarrantyRelevant: Boolean = false,
    /** Daily pre-ride checks batch into the weekly digest; they never notify individually (§8.2). */
    val isDailyCheck: Boolean = false,
    /** Some handbook items happen once at a given odometer rather than on a repeating cycle. */
    val isOneOff: Boolean = false,
    /** For items whose first occurrence is offset from the cycle, e.g. spark plug check at 16,000 km. */
    val firstDueKm: Int?,
    val notes: String?,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
)

/**
 * What an event did to a component. This is the thing that resets an interval, and it
 * is why a service visit is one event rather than seven (§6.2).
 */
@Entity(
    tableName = "component_action",
    foreignKeys = [ForeignKey(EventEntity::class, ["id"], ["eventId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("eventId"), Index("componentKey")],
)
data class ComponentActionEntity(
    @PrimaryKey val id: String = newId(),
    val eventId: String,
    val componentKey: String,
    /** replaced · serviced · checked · adjusted · topped_up */
    val action: String,
    val partUsed: String?,
    val notes: String?,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
)

// ---------------------------------------------------------------- documents & reminders

@Entity(
    tableName = "document",
    foreignKeys = [ForeignKey(EventEntity::class, ["id"], ["eventId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("eventId"), Index("docType"), Index("expiresOn")],
)
data class DocumentEntity(
    @PrimaryKey val id: String = newId(),
    val eventId: String,
    /** insurance · puc · rc · licence · warranty · invoice · service_plan · rsa · loan · other */
    val docType: String,
    val issuer: String?,
    val number: String?,
    val issuedOn: Long?,
    val expiresOn: Long?,
    /**
     * Indian new-vehicle insurance commonly bundles a multi-year third-party cover with
     * an annually-renewed own-damage cover. One expiry field would silently produce a
     * wrong reminder for the one that matters (§6.1).
     */
    val secondaryExpiresOn: Long?,
    val amountPaise: Long?,
    val fileUri: String?,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
)

@Entity(tableName = "reminder", indices = [Index("componentKey"), Index("documentId"), Index("dueDate")])
data class ReminderEntity(
    @PrimaryKey val id: String = newId(),
    val componentKey: String?,
    val documentId: String?,
    /** km · time · whichever_first */
    val ruleType: String,
    val dueOdometerKm: Int?,
    val dueDate: Long?,
    /** info · due_soon · due · overdue · warranty */
    val severity: String,
    val snoozedUntil: Long?,
    val lastNotifiedAt: Long?,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
)

// ---------------------------------------------------------------- supporting tables

@Entity(tableName = "vendor")
data class VendorEntity(
    @PrimaryKey val id: String = newId(),
    val name: String,
    /** workshop · fuel_station · parts_shop · insurer · rto · other */
    val kind: String,
    val phone: String?,
    val address: String?,
    val notes: String?,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
)

@Entity(
    tableName = "attachment",
    foreignKeys = [ForeignKey(EventEntity::class, ["id"], ["eventId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("eventId")],
)
data class AttachmentEntity(
    @PrimaryKey val id: String = newId(),
    val eventId: String,
    /** image · pdf · audio */
    val kind: String,
    val fileUri: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val caption: String?,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
)

/**
 * Plan §5.2 — the Capture Inbox. On a tablet-only build this is the app's only route in,
 * so an imported image lands here first and is structured later.
 */
@Entity(tableName = "capture_inbox", indices = [Index("status"), Index("importedAt")])
data class CaptureInboxEntity(
    @PrimaryKey val id: String = newId(),
    val fileUri: String,
    val mimeType: String?,
    /** gallery · share · drag_drop */
    val importSource: String,
    /** pending · ocr_done · filed · dismissed */
    val status: String = "pending",
    val ocrText: String?,
    /** The event this item eventually became, once filed. */
    val filedAsEventId: String?,
    val importedAt: Long = now(),
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
)

@Entity(
    tableName = "fault",
    foreignKeys = [ForeignKey(EventEntity::class, ["id"], ["eventId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("eventId"), Index("status")],
)
data class FaultEntity(
    @PrimaryKey val id: String = newId(),
    val eventId: String,
    val summary: String,
    /** open · watching · resolved · accepted */
    val status: String = "open",
    val firstNoticedOdometerKm: Int?,
    val resolvedByEventId: String?,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
)

@Entity(
    tableName = "ride",
    foreignKeys = [ForeignKey(EventEntity::class, ["id"], ["eventId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("eventId")],
)
data class RideEntity(
    @PrimaryKey val id: String = newId(),
    val eventId: String,
    val startedAt: Long,
    val endedAt: Long?,
    val startOdometerKm: Int?,
    val endOdometerKm: Int?,
    val route: String?,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
)

@Entity(tableName = "inventory_item")
data class InventoryItemEntity(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val componentKey: String?,
    val quantity: Double,
    val unit: String?,
    val notes: String?,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
)

/**
 * Plan §10.3 — the curated fact table. Small, hand-verifiable, and the authority for
 * §10.5's safety rule.
 *
 * [verifiedOn] is null until Pranav's own eyes have been on the handbook page. Rows
 * seeded in Phase 0 were read out of the PDF's text layer with a page citation, which
 * is good enough to display as "manual, unconfirmed" but NOT good enough to be the
 * final word on a torque figure. Appendix B Prompt 5 is the promotion step.
 */
@Entity(tableName = "fact", indices = [Index(value = ["key"], unique = true), Index("category")])
data class FactEntity(
    @PrimaryKey val id: String = newId(),
    val key: String,
    val label: String,
    val value: String,
    val unit: String?,
    val category: String,
    /** manual · dealer · community · mine */
    val source: String,
    val pageRef: Int?,
    val isSafetyCritical: Boolean,
    val notes: String?,
    /** Epoch millis of the owner's own verification, or null. */
    val verifiedOn: Long?,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
)

@Entity(tableName = "setting")
data class SettingEntity(
    @PrimaryKey @ColumnInfo(name = "key") val key: String,
    val value: String?,
    val updatedAt: Long = now(),
)
