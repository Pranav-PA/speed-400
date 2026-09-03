package dev.pranav.speed400garage.domain.engine

/** How full the tank was left. Only [FULL] can close a measurable span. */
enum class FillType { FIRST, PARTIAL, FULL }

data class Fill(
    val id: String,
    val epochDay: Long,
    val odometerKm: Int?,
    val litres: Double,
    val fillType: FillType,
    /** Set when a fill is known to have gone unrecorded before this one. */
    val missedPrevious: Boolean = false,
    val amountPaise: Long = 0,
)

/**
 * One measured full-to-full span. [kmpl] is only ever produced from two full fills
 * with real odometer readings.
 */
data class TankSpan(
    val fromFillId: String,
    val toFillId: String,
    val endedOnDay: Long,
    val km: Int,
    val litres: Double,
) {
    val kmpl: Double get() = km / litres
}

data class EconomyReport(
    val spans: List<TankSpan>,
    /** Distance-weighted mean over the most recent spans, or null if there are none. */
    val rollingKmpl: Double?,
    val rollingWindow: Int,
) {
    val latestKmpl: Double? get() = spans.lastOrNull()?.kmpl
}

/**
 * Fuel economy, full-to-full only (§9.1).
 *
 * A single tank's mileage is only meaningful between two full fills; partial fills in
 * between are accumulated, never treated as data points of their own. The alternative —
 * dividing each fill's litres into the km since the last one — produces numbers that
 * look plausible and are wrong, which is worse than producing nothing.
 */
object FuelEconomyCalculator {

    /**
     * The dashboard headline averages this many spans. Single-tank figures are noisy
     * enough — traffic, pillion, weather, how full "full" actually was — to mislead on
     * their own (§9.1).
     */
    const val ROLLING_SPANS = 5

    fun report(fills: List<Fill>, rollingWindow: Int = ROLLING_SPANS): EconomyReport {
        val spans = spans(fills)
        return EconomyReport(
            spans = spans,
            rollingKmpl = rollingKmpl(spans, rollingWindow),
            rollingWindow = rollingWindow,
        )
    }

    /**
     * For each full fill, the span back to the previous full fill:
     *
     *     km     = odo(Fi) − odo(F(i−1))
     *     litres = Σ litres of every fill after F(i−1) up to and including Fi
     *
     * The first fill establishes a baseline and yields nothing. A fill marked
     * [Fill.missedPrevious] discards the baseline and the accumulated litres, so the
     * next full fill starts fresh rather than reporting a span whose litres are missing
     * a tankful.
     */
    fun spans(fills: List<Fill>): List<TankSpan> {
        val ordered = fills.sortedWith(compareBy({ it.epochDay }, { it.odometerKm ?: Int.MAX_VALUE }))
        val out = mutableListOf<TankSpan>()

        var baseline: Fill? = null
        var pendingLitres = 0.0

        for (fill in ordered) {
            if (fill.missedPrevious) {
                baseline = null
                pendingLitres = 0.0
            }

            pendingLitres += fill.litres

            if (fill.fillType == FillType.PARTIAL) continue

            // FULL or FIRST: closes a span if there is a usable baseline.
            val from = baseline
            val fromOdo = from?.odometerKm
            val toOdo = fill.odometerKm
            if (from != null && fromOdo != null && toOdo != null && pendingLitres > 0.0) {
                val km = toOdo - fromOdo
                // A non-positive span means the odometers are wrong, not that the bike
                // did zero km. Emitting it would produce an absurd kmpl.
                if (km > 0) {
                    out += TankSpan(
                        fromFillId = from.id,
                        toFillId = fill.id,
                        endedOnDay = fill.epochDay,
                        km = km,
                        litres = pendingLitres,
                    )
                }
            }

            baseline = fill
            pendingLitres = 0.0
        }
        return out
    }

    /**
     * Distance-weighted: Σkm / Σlitres over the window, not the mean of the per-span
     * figures. A 40 km span and a 400 km span are not equally good evidence, and the
     * unweighted mean would let the short one distort the headline.
     */
    fun rollingKmpl(spans: List<TankSpan>, window: Int = ROLLING_SPANS): Double? {
        val recent = spans.takeLast(window)
        if (recent.isEmpty()) return null
        val km = recent.sumOf { it.km }
        val litres = recent.sumOf { it.litres }
        return if (litres > 0.0) km / litres else null
    }
}
