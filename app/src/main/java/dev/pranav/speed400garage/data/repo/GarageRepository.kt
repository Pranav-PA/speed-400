package dev.pranav.speed400garage.data.repo

import dev.pranav.speed400garage.data.db.GarageDatabase
import dev.pranav.speed400garage.data.db.entity.ComponentActionEntity
import dev.pranav.speed400garage.data.db.entity.DocumentEntity
import dev.pranav.speed400garage.data.db.entity.EventEntity
import dev.pranav.speed400garage.data.db.entity.FaultEntity
import dev.pranav.speed400garage.data.db.entity.FuelEntryEntity
import dev.pranav.speed400garage.data.db.entity.LineItemEntity
import dev.pranav.speed400garage.data.db.entity.OdometerReadingEntity
import dev.pranav.speed400garage.data.db.entity.newId
import dev.pranav.speed400garage.domain.engine.Baseline
import dev.pranav.speed400garage.domain.engine.Fill
import dev.pranav.speed400garage.domain.engine.FillType
import dev.pranav.speed400garage.domain.engine.Reading
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns real-world happenings into rows, and rows back into the shapes the engines eat.
 *
 * The one rule this file exists to enforce: a single happening is a single event
 * (§3 P2). Logging a fuel fill writes an event, a line item, a fuel facet AND an
 * odometer reading — in one transaction — so the timeline, the spend total, the
 * economy chart and the projection can never disagree about whether it happened.
 */
@Singleton
class GarageRepository @Inject constructor(
    private val db: GarageDatabase,
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun epochDayOf(millis: Long): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

    fun today(): Long = LocalDate.now(zone).toEpochDay()

    fun millisOf(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()

    suspend fun activeBikeId(): String? = db.bikeDao().activeBike()?.id

    // ------------------------------------------------------------------ writes

    /**
     * A fuel fill: one event carrying money, litres and an odometer reading.
     *
     * Entry is ₹-first (§7.2) — the caller passes what the pump charged and the rate,
     * and litres are derived. That is the order an Indian pump actually works in, and
     * asking for litres first would be a small tax on every single fill.
     */
    suspend fun logFuel(
        bikeId: String,
        occurredAtMillis: Long,
        odometerKm: Int?,
        amountPaise: Long,
        pricePerLitrePaise: Long?,
        litres: Double,
        fillType: FillType,
        missedPrevious: Boolean,
        isComputedLitres: Boolean,
        station: String?,
        notes: String?,
    ): String {
        val eventId = newId()
        db.eventWriteDao().writeEvent(
            event = EventEntity(
                id = eventId,
                bikeId = bikeId,
                type = "fuel",
                occurredAt = occurredAtMillis,
                odometerKm = odometerKm,
                title = station?.takeIf { it.isNotBlank() } ?: "Fuel",
                notes = notes,
                vendorId = null,
                location = null,
            ),
            lineItems = listOf(
                LineItemEntity(
                    eventId = eventId,
                    category = "fuel",
                    description = pricePerLitrePaise?.let { "%.2f L @ ₹%.2f/L".format(litres, it / 100.0) },
                    qty = litres,
                    unitPricePaise = pricePerLitrePaise,
                    amountPaise = amountPaise,
                )
            ),
            fuelEntry = FuelEntryEntity(
                eventId = eventId,
                litres = litres,
                pricePerLitrePaise = pricePerLitrePaise,
                amountPaise = amountPaise,
                fillType = fillType.name.lowercase(),
                missedPrevious = missedPrevious,
                stationId = null,
                fuelGrade = null,
                isComputedLitres = isComputedLitres,
            ),
            // Every fill captures a reading for free — this is the virtuous loop in
            // §5.1 that keeps the whole reminder system accurate at zero extra effort.
            odometerReading = odometerKm?.let {
                OdometerReadingEntity(bikeId = bikeId, eventId = eventId, readAt = occurredAtMillis, odometerKm = it)
            },
        )
        return eventId
    }

    /** An arbitrary expense. Money lives only in the line item (§4.2). */
    suspend fun logExpense(
        bikeId: String,
        occurredAtMillis: Long,
        odometerKm: Int?,
        title: String,
        category: String,
        amountPaise: Long,
        notes: String?,
    ): String {
        val eventId = newId()
        db.eventWriteDao().writeEvent(
            event = EventEntity(
                id = eventId,
                bikeId = bikeId,
                type = if (category in SERVICE_CATEGORIES) "service" else "part",
                occurredAt = occurredAtMillis,
                odometerKm = odometerKm,
                title = title.ifBlank { category.replaceFirstChar { c -> c.uppercase() } },
                notes = notes,
                vendorId = null,
                location = null,
            ),
            lineItems = listOf(
                LineItemEntity(eventId = eventId, category = category, description = null, qty = null, unitPricePaise = null, amountPaise = amountPaise)
            ),
            odometerReading = odometerKm?.let {
                OdometerReadingEntity(bikeId = bikeId, eventId = eventId, readAt = occurredAtMillis, odometerKm = it)
            },
        )
        return eventId
    }

    /**
     * A service or part replacement: money AND the component resets it causes.
     *
     * This is why a workshop visit is one event rather than seven (§6.2) — the line
     * items and the component actions hang off the same row, so replacing brake pads
     * shows up in spend, in the timeline and in the maintenance state from one entry.
     */
    suspend fun logService(
        bikeId: String,
        occurredAtMillis: Long,
        odometerKm: Int?,
        title: String,
        vendorName: String?,
        lines: List<Pair<String, Long>>,
        componentKeys: List<String>,
        action: String,
        notes: String?,
    ): String {
        val eventId = newId()
        db.eventWriteDao().writeEvent(
            event = EventEntity(
                id = eventId,
                bikeId = bikeId,
                type = "service",
                occurredAt = occurredAtMillis,
                odometerKm = odometerKm,
                title = title.ifBlank { "Service" },
                notes = listOfNotNull(vendorName?.takeIf { it.isNotBlank() }?.let { "At $it" }, notes)
                    .joinToString("\n").ifBlank { null },
                vendorId = null,
                location = null,
            ),
            lineItems = lines.map { (category, paise) ->
                LineItemEntity(eventId = eventId, category = category, description = null, qty = null, unitPricePaise = null, amountPaise = paise)
            },
            componentActions = componentKeys.map { key ->
                ComponentActionEntity(eventId = eventId, componentKey = key, action = action, partUsed = null, notes = null)
            },
            odometerReading = odometerKm?.let {
                OdometerReadingEntity(bikeId = bikeId, eventId = eventId, readAt = occurredAtMillis, odometerKm = it)
            },
        )
        return eventId
    }

    /** A bare odometer reading — one tap and one number (§3 P1). */
    suspend fun logOdometer(bikeId: String, occurredAtMillis: Long, odometerKm: Int): String {
        val eventId = newId()
        db.eventWriteDao().writeEvent(
            event = EventEntity(
                id = eventId,
                bikeId = bikeId,
                type = "odo_reading",
                occurredAt = occurredAtMillis,
                odometerKm = odometerKm,
                title = "Odometer",
                notes = null,
                vendorId = null,
                location = null,
            ),
            odometerReading = OdometerReadingEntity(
                bikeId = bikeId, eventId = eventId, readAt = occurredAtMillis, odometerKm = odometerKm,
            ),
        )
        return eventId
    }

    /**
     * A document with its expiry, and the premium as a real expense.
     *
     * The premium flows into the expense total automatically (§7.6) — entering an
     * insurance renewal in two places is exactly the double-entry §4.2 exists to
     * prevent.
     */
    suspend fun logDocument(
        bikeId: String,
        occurredAtMillis: Long,
        docType: String,
        issuer: String?,
        number: String?,
        issuedOnMillis: Long?,
        expiresOnMillis: Long?,
        secondaryExpiresOnMillis: Long?,
        amountPaise: Long?,
        notes: String?,
    ): String {
        val eventId = newId()
        val category = when (docType) {
            "insurance" -> "insurance"
            "puc" -> "puc"
            "rc" -> "rto"
            else -> "other"
        }
        db.eventWriteDao().writeEvent(
            event = EventEntity(
                id = eventId,
                bikeId = bikeId,
                type = "document",
                occurredAt = occurredAtMillis,
                odometerKm = null,
                title = docType.replaceFirstChar { it.uppercase() } + (issuer?.let { " — $it" } ?: ""),
                notes = notes,
                vendorId = null,
                location = null,
            ),
            lineItems = amountPaise?.takeIf { it > 0 }?.let {
                listOf(LineItemEntity(eventId = eventId, category = category, description = number, qty = null, unitPricePaise = null, amountPaise = it))
            } ?: emptyList(),
        )
        db.documentDao().insert(
            DocumentEntity(
                eventId = eventId,
                docType = docType,
                issuer = issuer,
                number = number,
                issuedOn = issuedOnMillis,
                expiresOn = expiresOnMillis,
                secondaryExpiresOn = secondaryExpiresOnMillis,
                amountPaise = amountPaise,
                fileUri = null,
            )
        )
        return eventId
    }

    /**
     * A niggle (§5.4). Not an expense, not a service — an open issue that stays open
     * until something closes it. By the time the service appointment comes round you
     * have forgotten three of the four things you meant to mention.
     */
    suspend fun logFault(
        bikeId: String,
        occurredAtMillis: Long,
        odometerKm: Int?,
        summary: String,
        notes: String?,
    ): String {
        val eventId = newId()
        db.eventWriteDao().writeEvent(
            event = EventEntity(
                id = eventId,
                bikeId = bikeId,
                type = "fault",
                occurredAt = occurredAtMillis,
                odometerKm = odometerKm,
                title = summary,
                notes = notes,
                vendorId = null,
                location = null,
            ),
            odometerReading = odometerKm?.let {
                OdometerReadingEntity(bikeId = bikeId, eventId = eventId, readAt = occurredAtMillis, odometerKm = it)
            },
        )
        db.faultDao().insert(
            FaultEntity(
                eventId = eventId,
                summary = summary,
                status = "open",
                firstNoticedOdometerKm = odometerKm,
                resolvedByEventId = null,
            )
        )
        return eventId
    }

    suspend fun setFaultStatus(id: String, status: String, resolvedByEventId: String? = null) {
        db.faultDao().setStatus(id, status, resolvedByEventId, System.currentTimeMillis())
    }

    // ------------------------------------------------------------------ reads

    suspend fun readings(bikeId: String): List<Reading> =
        db.odometerDao().recentReadings(bikeId, limit = 400)
            .map { Reading(epochDayOf(it.readAt), it.odometerKm) }

    suspend fun fills(bikeId: String): List<Fill> =
        db.fuelDao().fillsFor(bikeId).map {
            Fill(
                id = it.eventId,
                epochDay = epochDayOf(it.occurredAt),
                odometerKm = it.odometerKm,
                litres = it.litres,
                fillType = runCatching { FillType.valueOf(it.fillType.uppercase()) }.getOrDefault(FillType.FULL),
                missedPrevious = it.missedPrevious,
                amountPaise = it.amountPaise,
            )
        }

    suspend fun lastPricePerLitrePaise(bikeId: String): Long? =
        db.fuelDao().fillsFor(bikeId).lastOrNull { it.pricePerLitrePaise != null }?.pricePerLitrePaise

    suspend fun lastOdometerKm(bikeId: String): Int? =
        db.odometerDao().recentReadings(bikeId, limit = 1).firstOrNull()?.odometerKm

    /**
     * Where each component's clock last restarted, and where the bike's own clock
     * started for components never touched.
     */
    suspend fun baselines(bikeId: String): Map<String, Baseline> =
        db.componentActionDao().lastActions(bikeId).mapNotNull { row ->
            row.lastActionAt?.let { at -> row.componentKey to Baseline(epochDayOf(at), row.lastActionOdometerKm) }
        }.toMap()

    suspend fun bikeStart(bikeId: String): Baseline? {
        val bike = db.bikeDao().activeBike() ?: return null
        val earliest = db.odometerDao().recentReadings(bikeId, limit = 400).minByOrNull { it.readAt }
        val day = bike.purchasedOn ?: earliest?.let { epochDayOf(it.readAt) } ?: return null
        return Baseline(day, earliest?.odometerKm ?: 0)
    }

    private companion object {
        val SERVICE_CATEGORIES = setOf("labour", "parts", "consumables")
    }
}
