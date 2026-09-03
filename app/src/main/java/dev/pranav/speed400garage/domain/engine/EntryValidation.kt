package dev.pranav.speed400garage.domain.engine

import kotlin.math.abs

/**
 * Something the app noticed about an entry as it was being saved.
 *
 * Every one of these is a QUESTION, not a rejection — with exactly one exception
 * ([Kind.ODOMETER_BACKWARDS]), which is arithmetically impossible rather than merely
 * surprising. The app never refuses data it does not understand; it records it and
 * flags it (§9.2).
 */
data class Finding(
    val kind: Kind,
    val message: String,
    val blocking: Boolean = false,
    /** Offered answers, where knowing *why* is what keeps the history honest. */
    val options: List<String> = emptyList(),
) {
    enum class Kind {
        ODOMETER_BACKWARDS,
        ODOMETER_JUMP,
        LITRES_OVER_TANK,
        KMPL_DEVIATION,
        RATE_DEVIATION,
    }
}

data class ValidationResult(val findings: List<Finding>) {
    val blocking: List<Finding> get() = findings.filter { it.blocking }
    val questions: List<Finding> get() = findings.filterNot { it.blocking }
    val canSave: Boolean get() = blocking.isEmpty()
}

/**
 * Entry-time checks (§9.2).
 *
 * Bad data poisons every chart downstream and is far easier to catch at entry than to
 * find six months later. The KMPL question in particular is what keeps the mileage
 * chart honest over ten years: a one-off bad tank is noise, but a *systematically*
 * missed fill is a bias, and only the rider knows which it was.
 */
object EntryValidator {

    /** Handbook p.199: 13 litres. Anything over is a jerrycan or a typo. */
    const val TANK_CAPACITY_LITRES = 13.0

    const val IMPLAUSIBLE_JUMP_KM = 1_500
    const val KMPL_DEVIATION_FRACTION = 0.40
    const val RATE_DEVIATION_FRACTION = 0.25

    fun validateOdometer(newKm: Int, lastKnownKm: Int?): List<Finding> {
        if (lastKnownKm == null) return emptyList()
        return when {
            newKm < lastKnownKm -> listOf(
                Finding(
                    kind = Finding.Kind.ODOMETER_BACKWARDS,
                    message = "That's below the last reading of ${"%,d".format(lastKnownKm)} km. " +
                        "An odometer can't go backwards — check the digits.",
                    blocking = true,
                )
            )
            newKm - lastKnownKm > IMPLAUSIBLE_JUMP_KM -> listOf(
                Finding(
                    kind = Finding.Kind.ODOMETER_JUMP,
                    message = "That's ${"%,d".format(newKm - lastKnownKm)} km since the last reading. " +
                        "Is that right?",
                    options = listOf("Yes, I rode that", "Typo — let me fix it"),
                )
            )
            else -> emptyList()
        }
    }

    fun validateFuel(
        litres: Double,
        pricePerLitrePaise: Long?,
        lastPricePerLitrePaise: Long?,
        computedKmpl: Double?,
        rollingKmpl: Double?,
    ): List<Finding> = buildList {
        if (litres > TANK_CAPACITY_LITRES) {
            add(
                Finding(
                    kind = Finding.Kind.LITRES_OVER_TANK,
                    message = "%.2f L is more than the 13 L tank (handbook p.199). Jerrycan, or a typo?"
                        .format(litres),
                    options = listOf("Jerrycan / topped up a can", "Typo — let me fix it"),
                )
            )
        }

        if (computedKmpl != null && rollingKmpl != null && rollingKmpl > 0) {
            val deviation = abs(computedKmpl - rollingKmpl) / rollingKmpl
            if (deviation > KMPL_DEVIATION_FRACTION) {
                val direction = if (computedKmpl < rollingKmpl) "below" else "above"
                add(
                    Finding(
                        kind = Finding.Kind.KMPL_DEVIATION,
                        message = "That tank works out at %.1f km/l, %.0f%% $direction your usual %.1f. Why?"
                            .format(computedKmpl, deviation * 100, rollingKmpl),
                        options = listOf(
                            "I missed logging a fill",
                            "That was a partial fill",
                            "Typo — let me fix it",
                            "Genuinely different riding",
                        ),
                    )
                )
            }
        }

        if (pricePerLitrePaise != null && lastPricePerLitrePaise != null && lastPricePerLitrePaise > 0) {
            val deviation = abs(pricePerLitrePaise - lastPricePerLitrePaise).toDouble() / lastPricePerLitrePaise
            if (deviation > RATE_DEVIATION_FRACTION) {
                add(
                    Finding(
                        kind = Finding.Kind.RATE_DEVIATION,
                        message = "₹%.2f/L is a long way from the ₹%.2f you last paid. Is that right?"
                            .format(pricePerLitrePaise / 100.0, lastPricePerLitrePaise / 100.0),
                        options = listOf("Yes, that's the rate", "Typo — let me fix it"),
                    )
                )
            }
        }
    }

    fun validate(
        odometerKm: Int?,
        lastKnownOdometerKm: Int?,
        litres: Double? = null,
        pricePerLitrePaise: Long? = null,
        lastPricePerLitrePaise: Long? = null,
        computedKmpl: Double? = null,
        rollingKmpl: Double? = null,
    ): ValidationResult = ValidationResult(
        buildList {
            if (odometerKm != null) addAll(validateOdometer(odometerKm, lastKnownOdometerKm))
            if (litres != null) {
                addAll(validateFuel(litres, pricePerLitrePaise, lastPricePerLitrePaise, computedKmpl, rollingKmpl))
            }
        }
    )
}
