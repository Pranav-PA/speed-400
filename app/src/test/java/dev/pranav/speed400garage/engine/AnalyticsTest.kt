package dev.pranav.speed400garage.engine

import dev.pranav.speed400garage.domain.engine.Analytics
import dev.pranav.speed400garage.domain.engine.Reading
import dev.pranav.speed400garage.domain.engine.SpendRow
import dev.pranav.speed400garage.domain.engine.TankSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AnalyticsTest {

    private val today = LocalDate.of(2026, 9, 3).toEpochDay()
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    @Test
    fun `km per month counts the gap between months, not just within them`() {
        // 10,000 on 30 Jun, 10,500 on 2 Jul, 11,000 on 30 Jul.
        // July's distance is 1,000 km — from the June reading — not the 500 km
        // visible between July's own two readings.
        val readings = listOf(
            Reading(day(2026, 6, 30), 10_000),
            Reading(day(2026, 7, 2), 10_500),
            Reading(day(2026, 7, 30), 11_000),
        )
        val july = Analytics.kmPerMonth(readings, months = 4, today = today)
            .single { it.month.monthValue == 7 }
        assertEquals(1_000.0, july.value, 1e-9)
    }

    @Test
    fun `a month with no readings reports zero rather than disappearing`() {
        val readings = listOf(Reading(day(2026, 6, 1), 10_000), Reading(day(2026, 9, 1), 11_000))
        val buckets = Analytics.kmPerMonth(readings, months = 4, today = today)
        assertEquals(4, buckets.size)
        assertEquals(0.0, buckets.single { it.month.monthValue == 7 }.value, 1e-9)
    }

    @Test
    fun `fewer than two readings cannot describe distance over time`() {
        assertTrue(Analytics.kmPerMonth(listOf(Reading(today, 1000)), today = today).isEmpty())
    }

    @Test
    fun `spend per month buckets by calendar month and pads the gaps`() {
        val rows = listOf(
            SpendRow("fuel", 50_000, day(2026, 8, 3)),
            SpendRow("fuel", 60_000, day(2026, 8, 20)),
            SpendRow("labour", 200_000, day(2026, 9, 1)),
        )
        val months = Analytics.spendPerMonth(rows, months = 3, today = today)
        assertEquals(3, months.size)
        assertEquals(110_000.0, months.single { it.month.monthValue == 8 }.value, 1e-9)
        assertEquals(0.0, months.first().value, 1e-9)
    }

    @Test
    fun `categories rank by spend and the tail folds into other`() {
        val rows = (1..10).map { SpendRow("cat$it", it * 10_000L, today) }
        val totals = Analytics.byCategory(rows, keep = 3)
        assertEquals(4, totals.size)
        assertEquals("cat10", totals[0].category)
        assertEquals("other", totals.last().category)
        // Shares must still add to 1 after folding, or the chart lies about the whole.
        assertEquals(1.0, totals.sumOf { it.share }, 1e-9)
    }

    @Test
    fun `no spend means no categories rather than a chart of zeroes`() {
        assertTrue(Analytics.byCategory(emptyList()).isEmpty())
    }

    @Test
    fun `usable range excludes the reserve the low fuel light warns about`() {
        // Handbook p.199: 13 L tank, low-fuel light at 3 L remaining. A range figure
        // that assumes you ride the tank dry is not one anyone can plan a trip on.
        assertEquals(300, Analytics.usableRangeKm(30.0))
        assertEquals(390, Analytics.fullTankRangeKm(30.0))
        assertNull(Analytics.usableRangeKm(null))
    }

    @Test
    fun `the rolling series smooths toward the recent window`() {
        val spans = (1..7).map { TankSpan("a$it", "b$it", it.toLong(), 300, 10.0) }
        val series = Analytics.rollingSeries(spans, window = 5)
        assertEquals(7, series.size)
        assertTrue(series.all { kotlin.math.abs(it - 30.0) < 1e-9 })
    }

    @Test
    fun `a bad tank moves the rolling series less than the raw point`() {
        val spans = (1..5).map { TankSpan("a$it", "b$it", it.toLong(), 300, 10.0) } +
            TankSpan("bad", "bad", 6L, 100, 10.0) // 10 km/l
        val series = Analytics.rollingSeries(spans, window = 5)
        val raw = spans.last().kmpl
        assertTrue("the rolling figure must be less alarmed than the raw one", series.last() > raw)
        assertTrue(series.last() < 30.0)
    }
}
