package dev.pranav.speed400garage.engine

import dev.pranav.speed400garage.domain.engine.OdometerProjector
import dev.pranav.speed400garage.domain.engine.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OdometerProjectionTest {

    @Test
    fun `no readings means no projection rather than a guess`() {
        assertNull(OdometerProjector.project(emptyList(), today = 100))
    }

    @Test
    fun `a single reading projects nothing forward but still reports the reading`() {
        val p = OdometerProjector.project(listOf(Reading(90, 5000)), today = 100)!!
        assertFalse(p.hasRate)
        assertEquals(5000, p.estimatedKm)
        assertEquals(10, p.daysSinceReading)
    }

    @Test
    fun `a steady rate projects linearly`() {
        val readings = (0..4).map { Reading(it * 10L, 1000 + it * 300) } // 30 km/day
        val p = OdometerProjector.project(readings, today = 45)!!
        assertEquals(30.0, p.kmPerDay, 1e-6)
        // last reading 2200 at day 40, +5 days at 30 km/day
        assertEquals(2350, p.estimatedKm)
    }

    @Test
    fun `the rate leans toward recent riding`() {
        // Long slow history, then a sudden burst. A flat mean would lag badly.
        val readings = mutableListOf<Reading>()
        var km = 0
        for (d in 0..10) { readings += Reading(d * 10L, km); km += 100 }   // 10 km/day
        readings += Reading(110, km + 1000)                                // 100 km/day
        val rate = OdometerProjector.rateKmPerDay(readings.sortedBy { it.epochDay })!!
        val flatMean = (readings.last().km - readings.first().km).toDouble() /
            (readings.last().epochDay - readings.first().epochDay)
        assertTrue("EWMA should react faster than a flat mean", rate > flatMean)
    }

    @Test
    fun `an estimate is marked stale after two weeks`() {
        val readings = listOf(Reading(0, 1000), Reading(10, 1300))
        assertFalse(OdometerProjector.project(readings, today = 20)!!.isStale)
        assertTrue(OdometerProjector.project(readings, today = 30)!!.isStale)
    }

    @Test
    fun `a projection never runs backwards from the last observed reading`() {
        val readings = listOf(Reading(0, 1000), Reading(10, 1300))
        val p = OdometerProjector.project(readings, today = 5)!!
        assertTrue(p.estimatedKm >= 1300)
    }

    @Test
    fun `same-day readings do not divide by zero`() {
        val readings = listOf(Reading(5, 1000), Reading(5, 1010), Reading(15, 1310))
        val p = OdometerProjector.project(readings, today = 20)!!
        assertTrue(p.kmPerDay.isFinite())
        assertEquals(30.0, p.kmPerDay, 1e-6)
    }

    @Test
    fun `a backwards interval is skipped rather than poisoning the rate`() {
        // Entry validation blocks this, but a restored backup could still contain it.
        val readings = listOf(Reading(0, 1000), Reading(10, 900), Reading(20, 1600))
        val rate = OdometerProjector.rateKmPerDay(readings)
        assertTrue(rate == null || rate > 0.0)
    }

    @Test
    fun `a km target becomes a date`() {
        val readings = (0..3).map { Reading(it * 10L, 1000 + it * 300) } // 30 km/day
        val p = OdometerProjector.project(readings, today = 30)!!
        assertEquals(1900, p.estimatedKm)
        // 300 km away at 30 km/day = 10 days
        assertEquals(40L, OdometerProjector.projectedDayFor(2200, p, today = 30))
    }

    @Test
    fun `a target already passed is due today, and no rate means no date`() {
        val readings = (0..3).map { Reading(it * 10L, 1000 + it * 300) }
        val p = OdometerProjector.project(readings, today = 30)!!
        assertEquals(30L, OdometerProjector.projectedDayFor(1500, p, today = 30))

        val single = OdometerProjector.project(listOf(Reading(0, 1000)), today = 30)!!
        assertNull(OdometerProjector.projectedDayFor(2000, single, today = 30))
    }
}
