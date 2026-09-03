package dev.pranav.speed400garage.ui.log

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Indian formatting throughout: ₹, litres, km (§2). */
object Fmt {
    private val dayFormat = DateTimeFormatter.ofPattern("d MMM yyyy")
    private val shortDay = DateTimeFormatter.ofPattern("d MMM")

    fun rupees(paise: Long): String {
        val whole = paise / 100
        val part = (paise % 100).toInt()
        return "₹" + groupIndian(whole) + if (part != 0) ".%02d".format(part) else ""
    }

    fun rupeesPerKm(paisePerKm: Double?): String =
        if (paisePerKm == null) "—" else "₹%.2f/km".format(paisePerKm / 100.0)

    /**
     * Indian digit grouping: 12,34,567 rather than 1,234,567. Getting this wrong is
     * a small thing that makes an app feel foreign every time you look at a total.
     */
    fun groupIndian(value: Long): String {
        val negative = value < 0
        val s = kotlin.math.abs(value).toString()
        if (s.length <= 3) return (if (negative) "-" else "") + s
        val last3 = s.takeLast(3)
        val rest = s.dropLast(3)
        val grouped = rest.reversed().chunked(2).joinToString(",").reversed()
        return (if (negative) "-" else "") + "$grouped,$last3"
    }

    fun km(value: Int): String = groupIndian(value.toLong()) + " km"

    fun litres(value: Double): String = "%.2f L".format(value)

    fun kmpl(value: Double?): String = if (value == null) "—" else "%.1f km/l".format(value)

    fun date(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(dayFormat)

    fun dateMillis(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(dayFormat)

    fun shortDate(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(shortDay)

    fun daysAgo(days: Int): String = when (days) {
        0 -> "today"
        1 -> "yesterday"
        else -> "$days days ago"
    }

    /** Parses "1,234.50" or "1234.5" into paise without going through a Double total. */
    fun parseRupeesToPaise(text: String): Long? {
        val cleaned = text.replace(",", "").replace("₹", "").trim()
        if (cleaned.isEmpty()) return null
        val value = cleaned.toDoubleOrNull() ?: return null
        if (value < 0) return null
        return Math.round(value * 100)
    }

    fun parseKm(text: String): Int? = text.replace(",", "").trim().toIntOrNull()?.takeIf { it >= 0 }
}
