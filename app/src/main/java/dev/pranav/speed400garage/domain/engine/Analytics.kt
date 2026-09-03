package dev.pranav.speed400garage.domain.engine

import java.time.LocalDate
import java.time.YearMonth

/** A spend row with its date, flattened for the analytics engine. */
data class SpendRow(val category: String, val paise: Long, val epochDay: Long)

data class MonthBucket(val month: YearMonth, val value: Double, val label: String)

data class CategoryTotal(val category: String, val paise: Long, val share: Double)

data class TankRange(val km: Int, val litres: Double)

/**
 * The numbers behind §11.
 *
 * Every one of these answers a question written down in the plan before the chart
 * existed. Nothing here computes a figure the app has no question for — the point of
 * §11 is that an analytics screen full of charts nobody asked for is worse than three
 * charts that answer something.
 */
object Analytics {

    /** "Am I riding more or less?" — km per calendar month, from observed readings only. */
    fun kmPerMonth(readings: List<Reading>, months: Int = 12, today: Long): List<MonthBucket> {
        if (readings.size < 2) return emptyList()
        val sorted = readings.sortedBy { it.epochDay }
        val end = YearMonth.from(LocalDate.ofEpochDay(today))
        val start = end.minusMonths((months - 1).toLong())

        // Distance in a month is the span between the first and last reading that
        // month, plus the gap from the previous month's last reading — anything else
        // would silently drop the kilometres ridden between two readings.
        val byMonth = sorted.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) }
        val out = mutableListOf<MonthBucket>()
        var cursor = start
        var previousKm: Int? = sorted.firstOrNull { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) < start }?.km
        while (!cursor.isAfter(end)) {
            val inMonth = byMonth[cursor].orEmpty().sortedBy { it.epochDay }
            val value = if (inMonth.isEmpty()) 0.0 else {
                val last = inMonth.last().km
                val base = previousKm ?: inMonth.first().km
                (last - base).coerceAtLeast(0).toDouble()
            }
            if (inMonth.isNotEmpty()) previousKm = inMonth.last().km
            out += MonthBucket(cursor, value, cursor.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() })
            cursor = cursor.plusMonths(1)
        }
        return out
    }

    /** "Which month is expensive?" — total spend per calendar month. */
    fun spendPerMonth(rows: List<SpendRow>, months: Int = 12, today: Long): List<MonthBucket> {
        val end = YearMonth.from(LocalDate.ofEpochDay(today))
        val start = end.minusMonths((months - 1).toLong())
        val byMonth = rows.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) }
        val out = mutableListOf<MonthBucket>()
        var cursor = start
        while (!cursor.isAfter(end)) {
            out += MonthBucket(
                cursor,
                byMonth[cursor].orEmpty().sumOf { it.paise }.toDouble(),
                cursor.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
            )
            cursor = cursor.plusMonths(1)
        }
        return out
    }

    /**
     * "Where does the money actually go?" — ranked, with a share of the total.
     *
     * Folds the tail into "Other" past [keep]. Thirteen categories is more colours
     * than anyone can tell apart, and a ranked list answers the question better than
     * a donut does anyway.
     */
    fun byCategory(rows: List<SpendRow>, keep: Int = 7): List<CategoryTotal> {
        val total = rows.sumOf { it.paise }
        if (total == 0L) return emptyList()
        val ranked = rows.groupBy { it.category }
            .map { (category, list) -> category to list.sumOf { it.paise } }
            .sortedByDescending { it.second }
        val head = ranked.take(keep)
        val tail = ranked.drop(keep)
        val out = head.map { (c, p) -> CategoryTotal(c, p, p.toDouble() / total) }
        return if (tail.isEmpty()) out
        else out + CategoryTotal("other", tail.sumOf { it.second }, tail.sumOf { it.second }.toDouble() / total)
    }

    /** "How far do I get on a tank?" — sets realistic trip planning. */
    fun tankRanges(spans: List<TankSpan>): List<TankRange> =
        spans.map { TankRange(it.km, it.litres) }

    /**
     * Range from a full tank at the rolling economy, using the handbook's 13 L capacity.
     *
     * Reported as usable range rather than tank capacity times mileage: the low-fuel
     * light comes on with 3 litres left (handbook p.199), and a range figure that
     * assumes you will ride the tank dry is not a figure anyone can plan on.
     */
    fun usableRangeKm(rollingKmpl: Double?, tankLitres: Double = 13.0, reserveLitres: Double = 3.0): Int? =
        rollingKmpl?.let { ((tankLitres - reserveLitres) * it).toInt() }

    fun fullTankRangeKm(rollingKmpl: Double?, tankLitres: Double = 13.0): Int? =
        rollingKmpl?.let { (tankLitres * it).toInt() }

    /**
     * "Is my mileage getting worse?" — the rolling series, one point per full tank.
     * Returned alongside the raw points so the chart can show both.
     */
    fun rollingSeries(spans: List<TankSpan>, window: Int = FuelEconomyCalculator.ROLLING_SPANS): List<Double> =
        spans.indices.map { i ->
            val slice = spans.subList(maxOf(0, i - window + 1), i + 1)
            slice.sumOf { it.km }.toDouble() / slice.sumOf { it.litres }
        }
}
