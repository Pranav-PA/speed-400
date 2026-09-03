package dev.pranav.speed400garage.domain.engine

/** A money row, in integer paise. Money never touches floating point in this app. */
data class Money(val paise: Long) {
    operator fun plus(other: Money) = Money(paise + other.paise)
    val rupees: Double get() = paise / 100.0
    companion object {
        val ZERO = Money(0)
        fun ofRupees(rupees: Double) = Money(Math.round(rupees * 100))
    }
}

/** One line item, flattened to what the cost engine needs. */
data class CostLine(val category: String, val paise: Long)

/**
 * The three cost-per-km numbers (§9.3 / §4.6).
 *
 * They are always reported together and always labelled, because a bare "cost per km"
 * is ambiguous enough to be useless — and the three differ by roughly an order of
 * magnitude in early ownership.
 */
data class CostPerKm(
    val fuelPaisePerKm: Double?,
    val runningPaisePerKm: Double?,
    val truePaisePerKm: Double?,
    val distanceKm: Int,
    val totalDistanceSincePurchaseKm: Int,
    /** True when [truePaisePerKm] includes a depreciation guess (always a 🟡 estimate). */
    val includesDepreciation: Boolean,
)

object CostEngine {

    /** Fuel only. */
    val FUEL_CATEGORIES = setOf("fuel")

    /**
     * Cost of keeping it on the road: fuel, plus the servicing and repairs that riding
     * it causes. Insurance, tax and gear are ownership costs rather than running costs,
     * so they land in the third number instead.
     */
    val RUNNING_CATEGORIES = setOf("fuel", "labour", "parts", "consumables")

    fun sum(lines: List<CostLine>, categories: Set<String>): Long =
        lines.filter { it.category in categories }.sumOf { it.paise }

    fun sumAll(lines: List<CostLine>): Long = lines.sumOf { it.paise }

    /**
     * @param windowLines line items inside the window being reported on.
     * @param windowDistanceKm odometer distance covered in that window.
     * @param allLines every line item since purchase — [truePaisePerKm] is a
     *        whole-ownership number and cannot be windowed meaningfully.
     * @param totalDistanceSincePurchaseKm odometer distance since purchase.
     * @param depreciationPaise purchase price minus the current value guess, or null
     *        when either is unknown. Omitting it would make the number a comfortable
     *        lie, so when it is absent the app must say so rather than quietly report
     *        a smaller figure (§9.3).
     */
    fun costPerKm(
        windowLines: List<CostLine>,
        windowDistanceKm: Int,
        allLines: List<CostLine>,
        totalDistanceSincePurchaseKm: Int,
        depreciationPaise: Long?,
    ): CostPerKm {
        fun perKm(paise: Long, km: Int): Double? = if (km > 0) paise.toDouble() / km else null

        val trueTotal = sumAll(allLines) + (depreciationPaise ?: 0L)

        return CostPerKm(
            fuelPaisePerKm = perKm(sum(windowLines, FUEL_CATEGORIES), windowDistanceKm),
            runningPaisePerKm = perKm(sum(windowLines, RUNNING_CATEGORIES), windowDistanceKm),
            truePaisePerKm = perKm(trueTotal, totalDistanceSincePurchaseKm),
            distanceKm = windowDistanceKm,
            totalDistanceSincePurchaseKm = totalDistanceSincePurchaseKm,
            includesDepreciation = depreciationPaise != null,
        )
    }

    /**
     * Straight-line depreciation between the purchase price and a current-value guess.
     * Deliberately crude: the guess is the user's, and dressing it up in a curve would
     * imply a precision that isn't there.
     */
    fun depreciation(purchasePricePaise: Long?, currentValueGuessPaise: Long?): Long? {
        if (purchasePricePaise == null || currentValueGuessPaise == null) return null
        return (purchasePricePaise - currentValueGuessPaise).coerceAtLeast(0L)
    }
}
