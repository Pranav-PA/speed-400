package dev.pranav.speed400garage.domain.engine

import kotlin.math.max
import kotlin.math.roundToInt

/** An observed odometer reading. Never an estimate — see [OdometerProjector]. */
data class Reading(val epochDay: Long, val km: Int)

/**
 * Today's odometer, as far as the app can tell.
 *
 * [isStale] is not cosmetic: past [OdometerProjector.STALE_AFTER_DAYS] the rate is
 * extrapolated far enough that km-based reminders should be downgraded rather than
 * trusted (§5.1).
 */
data class Projection(
    val estimatedKm: Int,
    val kmPerDay: Double,
    val lastReading: Reading,
    val daysSinceReading: Int,
    val isStale: Boolean,
    /** False when there is only one reading, so no rate could be derived. */
    val hasRate: Boolean,
)

/**
 * Projects today's odometer from past readings (§5.1).
 *
 * The Speed 400 has no telemetry, so the app only knows the odometer when it is told.
 * Every km-based reminder still needs to know where the bike is *now* — "chain lube due
 * at 12,400 km" is useless without that — so a rolling km/day rate is maintained and
 * extrapolated.
 *
 * The rate is exponentially weighted toward recent readings: riding patterns change
 * (a tour, a month off the bike), and a flat mean over all history would take far too
 * long to notice.
 *
 * Nothing here is ever written to the database. A projection that got persisted would
 * later be indistinguishable from an observation, and the whole point of P3 is that
 * the app knows the difference.
 */
object OdometerProjector {

    /** Weight given to the newest interval. Higher reacts faster and is noisier. */
    const val ALPHA = 0.4

    /** Past this, the estimate is shown as stale and projections are downgraded. */
    const val STALE_AFTER_DAYS = 14

    /**
     * @param readings observed readings in any order; duplicates on a day are fine.
     * @param today the day to project to.
     * @return null when there are no readings at all — the app must then ask for one
     *         rather than invent a number.
     */
    fun project(readings: List<Reading>, today: Long): Projection? {
        if (readings.isEmpty()) return null

        val sorted = readings.sortedWith(compareBy({ it.epochDay }, { it.km }))
        val last = sorted.last()
        val daysSince = max(0, (today - last.epochDay).toInt())

        val rate = rateKmPerDay(sorted)
        val estimated = if (rate == null) last.km
        else last.km + (rate * daysSince).roundToInt()

        return Projection(
            // A projection can never run backwards from the last thing actually seen.
            estimatedKm = max(last.km, estimated),
            kmPerDay = rate ?: 0.0,
            lastReading = last,
            daysSinceReading = daysSince,
            isStale = daysSince > STALE_AFTER_DAYS,
            hasRate = rate != null,
        )
    }

    /**
     * Exponentially weighted km/day across consecutive readings.
     *
     * Intervals with no elapsed days are skipped rather than dividing by zero, and so
     * are backwards intervals — those are a data error that §9.2 catches at entry, and
     * silently averaging them in would quietly corrupt every projection afterwards.
     */
    fun rateKmPerDay(sortedReadings: List<Reading>): Double? {
        var ewma: Double? = null
        for (i in 1 until sortedReadings.size) {
            val prev = sortedReadings[i - 1]
            val curr = sortedReadings[i]
            val days = curr.epochDay - prev.epochDay
            val km = curr.km - prev.km
            if (days <= 0 || km < 0) continue
            val rate = km.toDouble() / days
            ewma = if (ewma == null) rate else ALPHA * rate + (1 - ALPHA) * ewma
        }
        return ewma
    }

    /**
     * The date a target odometer is expected to be reached, or null when there is no
     * usable rate. Targets already passed come back as [today].
     */
    fun projectedDayFor(targetKm: Int, projection: Projection, today: Long): Long? {
        if (!projection.hasRate || projection.kmPerDay <= 0.0) return null
        val remaining = targetKm - projection.estimatedKm
        if (remaining <= 0) return today
        return today + Math.ceil(remaining / projection.kmPerDay).toLong()
    }
}
