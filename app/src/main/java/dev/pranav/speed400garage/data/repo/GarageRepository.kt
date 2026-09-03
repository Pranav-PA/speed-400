package dev.pranav.speed400garage.data.repo

import dev.pranav.speed400garage.data.db.GarageDatabase
import dev.pranav.speed400garage.data.db.entity.ComponentActionEntity
import dev.pranav.speed400garage.data.db.entity.EventEntity
import dev.pranav.speed400garage.data.db.entity.FuelEntryEntity
import dev.pranav.speed400garage.data.db.entity.LineItemEntity
import dev.pranav.speed400garage.data.db.entity.OdometerReadingEntity
import dev.pranav.speed400garage.data.db.entity.newId
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

    private companion object {
        val SERVICE_CATEGORIES = setOf("labour", "parts", "consumables")
    }
}
