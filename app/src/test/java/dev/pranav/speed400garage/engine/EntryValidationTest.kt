package dev.pranav.speed400garage.engine

import dev.pranav.speed400garage.domain.engine.EntryValidator
import dev.pranav.speed400garage.domain.engine.Finding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryValidationTest {

    @Test
    fun `a backwards odometer is the one thing that blocks`() {
        val r = EntryValidator.validate(odometerKm = 900, lastKnownOdometerKm = 1000)
        assertFalse(r.canSave)
        assertEquals(Finding.Kind.ODOMETER_BACKWARDS, r.blocking.single().kind)
    }

    @Test
    fun `an implausible jump asks rather than refuses`() {
        val r = EntryValidator.validate(odometerKm = 5000, lastKnownOdometerKm = 1000)
        assertTrue("a surprising number is still data", r.canSave)
        assertEquals(Finding.Kind.ODOMETER_JUMP, r.questions.single().kind)
        assertTrue(r.questions.single().options.isNotEmpty())
    }

    @Test
    fun `a normal ride raises nothing`() {
        assertTrue(EntryValidator.validate(1200, 1000).findings.isEmpty())
    }

    @Test
    fun `the first ever reading has nothing to compare against`() {
        assertTrue(EntryValidator.validate(1200, null).findings.isEmpty())
    }

    @Test
    fun `more litres than the tank holds asks why`() {
        val r = EntryValidator.validate(
            odometerKm = 1200, lastKnownOdometerKm = 1000, litres = 14.5,
        )
        assertTrue(r.canSave)
        assertEquals(Finding.Kind.LITRES_OVER_TANK, r.questions.single().kind)
        assertTrue("cites the handbook", r.questions.single().message.contains("13 L"))
    }

    @Test
    fun `a tank well off the usual asks why, with answers that matter`() {
        val r = EntryValidator.validate(
            odometerKm = 1200, lastKnownOdometerKm = 1000,
            litres = 10.0, computedKmpl = 15.0, rollingKmpl = 32.0,
        )
        val q = r.questions.single { it.kind == Finding.Kind.KMPL_DEVIATION }
        // "I missed logging a fill" is the option that keeps ten years of chart honest.
        assertTrue(q.options.any { it.contains("missed", ignoreCase = true) })
        assertTrue(q.options.any { it.contains("partial", ignoreCase = true) })
    }

    @Test
    fun `a tank close to the usual is left alone`() {
        val r = EntryValidator.validate(
            odometerKm = 1200, lastKnownOdometerKm = 1000,
            litres = 10.0, computedKmpl = 30.0, rollingKmpl = 32.0,
        )
        assertTrue(r.findings.none { it.kind == Finding.Kind.KMPL_DEVIATION })
    }

    @Test
    fun `a wild fuel rate asks, a normal one does not`() {
        val wild = EntryValidator.validate(
            odometerKm = 1200, lastKnownOdometerKm = 1000, litres = 10.0,
            pricePerLitrePaise = 20_000, lastPricePerLitrePaise = 10_500,
        )
        assertTrue(wild.questions.any { it.kind == Finding.Kind.RATE_DEVIATION })

        val normal = EntryValidator.validate(
            odometerKm = 1200, lastKnownOdometerKm = 1000, litres = 10.0,
            pricePerLitrePaise = 10_700, lastPricePerLitrePaise = 10_500,
        )
        assertTrue(normal.findings.isEmpty())
    }

    @Test
    fun `nothing except a backwards odometer ever blocks a save`() {
        val r = EntryValidator.validate(
            odometerKm = 9000, lastKnownOdometerKm = 1000,
            litres = 20.0, pricePerLitrePaise = 30_000, lastPricePerLitrePaise = 10_000,
            computedKmpl = 5.0, rollingKmpl = 35.0,
        )
        assertEquals(4, r.questions.size)
        assertTrue("the app records what it doesn't understand", r.canSave)
    }
}
