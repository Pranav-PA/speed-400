package dev.pranav.speed400garage.engine

import dev.pranav.speed400garage.domain.engine.Fill
import dev.pranav.speed400garage.domain.engine.FillType
import dev.pranav.speed400garage.domain.engine.FuelEconomyCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelEconomyTest {

    private var seq = 0
    private fun fill(
        day: Long,
        odo: Int?,
        litres: Double,
        type: FillType = FillType.FULL,
        missed: Boolean = false,
    ) = Fill("f${seq++}", day, odo, litres, type, missed)

    @Test
    fun `a single fill establishes a baseline and yields nothing`() {
        val spans = FuelEconomyCalculator.spans(listOf(fill(1, 1000, 10.0, FillType.FIRST)))
        assertTrue(spans.isEmpty())
    }

    @Test
    fun `two full fills give one span`() {
        val spans = FuelEconomyCalculator.spans(
            listOf(
                fill(1, 1000, 10.0, FillType.FIRST),
                fill(10, 1350, 10.0),
            )
        )
        assertEquals(1, spans.size)
        assertEquals(350, spans[0].km)
        // The baseline fill's own litres must NOT count toward the span.
        assertEquals(10.0, spans[0].litres, 1e-9)
        assertEquals(35.0, spans[0].kmpl, 1e-9)
    }

    @Test
    fun `partial fills accumulate into the next full span and never emit alone`() {
        val spans = FuelEconomyCalculator.spans(
            listOf(
                fill(1, 1000, 10.0, FillType.FIRST),
                fill(5, 1150, 4.0, FillType.PARTIAL),
                fill(12, 1400, 8.0, FillType.FULL),
            )
        )
        assertEquals("a partial must not produce a span of its own", 1, spans.size)
        assertEquals(400, spans[0].km)
        assertEquals(12.0, spans[0].litres, 1e-9)
    }

    @Test
    fun `a missed fill breaks the chain instead of producing a wrong number`() {
        val spans = FuelEconomyCalculator.spans(
            listOf(
                fill(1, 1000, 10.0, FillType.FIRST),
                // 600 km on 10 L would read as 60 km/l if the gap were ignored.
                fill(20, 1600, 10.0, FillType.FULL, missed = true),
                fill(30, 1900, 9.0, FillType.FULL),
            )
        )
        assertEquals(1, spans.size)
        assertEquals("the surviving span must start AFTER the break", 300, spans[0].km)
        assertEquals(9.0, spans[0].litres, 1e-9)
    }

    @Test
    fun `a missed fill also discards litres accumulated before it`() {
        val spans = FuelEconomyCalculator.spans(
            listOf(
                fill(1, 1000, 10.0, FillType.FIRST),
                fill(5, 1200, 5.0, FillType.PARTIAL),
                fill(9, 1400, 6.0, FillType.FULL, missed = true),
                fill(20, 1750, 10.0, FillType.FULL),
            )
        )
        assertEquals(1, spans.size)
        assertEquals(350, spans[0].km)
        assertEquals("only the litres after the break count", 10.0, spans[0].litres, 1e-9)
    }

    @Test
    fun `fills without an odometer cannot close a span`() {
        val spans = FuelEconomyCalculator.spans(
            listOf(
                fill(1, 1000, 10.0, FillType.FIRST),
                fill(10, null, 10.0, FillType.FULL),
                fill(20, 1700, 9.0, FillType.FULL),
            )
        )
        // The middle fill becomes the baseline but has no odometer, so the span it
        // would anchor is not measurable. Reporting one anyway would invent distance.
        assertEquals(0, spans.size)
    }

    @Test
    fun `a non-positive distance is refused rather than reported`() {
        val spans = FuelEconomyCalculator.spans(
            listOf(
                fill(1, 2000, 10.0, FillType.FIRST),
                fill(10, 2000, 10.0, FillType.FULL),
            )
        )
        assertTrue("zero km would produce an infinite kmpl", spans.isEmpty())
    }

    @Test
    fun `rolling average is distance-weighted over the last five spans`() {
        val fills = mutableListOf(fill(0, 0, 10.0, FillType.FIRST))
        var odo = 0
        var day = 0L
        // Six spans of 300 km on 10 L (30 km/l), so the window drops the oldest.
        repeat(6) {
            odo += 300; day += 7
            fills += fill(day, odo, 10.0, FillType.FULL)
        }
        val report = FuelEconomyCalculator.report(fills)
        assertEquals(6, report.spans.size)
        assertEquals(30.0, report.rollingKmpl!!, 1e-9)

        // A short, thirsty span must not drag the headline as much as a long one would
        // under an unweighted mean.
        odo += 20; day += 1
        fills += fill(day, odo, 4.0, FillType.FULL) // 5 km/l over just 20 km
        val skewed = FuelEconomyCalculator.report(fills)
        val unweightedMean = skewed.spans.takeLast(5).map { it.kmpl }.average()
        assertTrue(
            "distance weighting should keep the headline above a naive mean",
            skewed.rollingKmpl!! > unweightedMean,
        )
    }

    @Test
    fun `no spans means no rolling number rather than zero`() {
        val report = FuelEconomyCalculator.report(listOf(fill(1, 1000, 10.0, FillType.FIRST)))
        assertNull(report.rollingKmpl)
        assertNull(report.latestKmpl)
    }

    @Test
    fun `fills given out of order are still read chronologically`() {
        val a = fill(30, 1900, 9.0, FillType.FULL)
        val b = fill(1, 1000, 10.0, FillType.FIRST)
        val c = fill(15, 1450, 12.0, FillType.FULL)
        val spans = FuelEconomyCalculator.spans(listOf(a, b, c))
        assertEquals(2, spans.size)
        assertEquals(450, spans[0].km)
        assertEquals(450, spans[1].km)
    }
}
