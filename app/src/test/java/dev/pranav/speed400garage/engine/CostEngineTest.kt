package dev.pranav.speed400garage.engine

import dev.pranav.speed400garage.domain.engine.CostEngine
import dev.pranav.speed400garage.domain.engine.CostLine
import dev.pranav.speed400garage.domain.engine.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CostEngineTest {

    private val lines = listOf(
        CostLine("fuel", 500_000),        // ₹5,000
        CostLine("labour", 150_000),      // ₹1,500
        CostLine("parts", 100_000),       // ₹1,000
        CostLine("consumables", 50_000),  // ₹500
        CostLine("insurance", 900_000),   // ₹9,000
        CostLine("gear", 400_000),        // ₹4,000
    )

    @Test
    fun `money is integer paise and never floating point`() {
        assertEquals(12345L, Money.ofRupees(123.45).paise)
        assertEquals(123.45, Money(12345).rupees, 1e-9)
        // The classic 0.1 + 0.2 problem must not exist here.
        assertEquals(30L, (Money.ofRupees(0.10) + Money.ofRupees(0.20)).paise)
    }

    @Test
    fun `the three numbers are genuinely different`() {
        val c = CostEngine.costPerKm(
            windowLines = lines,
            windowDistanceKm = 1000,
            allLines = lines,
            totalDistanceSincePurchaseKm = 1000,
            depreciationPaise = 5_000_000, // ₹50,000
        )
        assertEquals(500.0, c.fuelPaisePerKm!!, 1e-9)                 // ₹5.00/km
        assertEquals(800.0, c.runningPaisePerKm!!, 1e-9)              // ₹8.00/km
        assertEquals(7100.0, c.truePaisePerKm!!, 1e-9)                // ₹71.00/km
        assertTrue(c.fuelPaisePerKm!! < c.runningPaisePerKm!!)
        assertTrue(c.runningPaisePerKm!! < c.truePaisePerKm!!)
    }

    @Test
    fun `insurance and gear are ownership costs not running costs`() {
        val running = CostEngine.sum(lines, CostEngine.RUNNING_CATEGORIES)
        assertEquals(800_000L, running)
        assertFalse("insurance", CostEngine.RUNNING_CATEGORIES.contains("insurance"))
        assertFalse("gear", CostEngine.RUNNING_CATEGORIES.contains("gear"))
    }

    @Test
    fun `a missing depreciation guess is declared rather than quietly dropped`() {
        val c = CostEngine.costPerKm(lines, 1000, lines, 1000, depreciationPaise = null)
        assertFalse(c.includesDepreciation)
        // Still reported, but the caller now knows it excludes the largest component.
        assertEquals(2100.0, c.truePaisePerKm!!, 1e-9)
    }

    @Test
    fun `zero distance yields null rather than a division by zero`() {
        val c = CostEngine.costPerKm(lines, 0, lines, 0, depreciationPaise = 1)
        assertNull(c.fuelPaisePerKm)
        assertNull(c.runningPaisePerKm)
        assertNull(c.truePaisePerKm)
    }

    @Test
    fun `depreciation never goes negative if the bike appreciates`() {
        assertEquals(0L, CostEngine.depreciation(100_000, 150_000))
        assertEquals(50_000L, CostEngine.depreciation(200_000, 150_000))
        assertNull(CostEngine.depreciation(null, 150_000))
        assertNull(CostEngine.depreciation(200_000, null))
    }

    @Test
    fun `the window narrows running cost but true cost stays whole-ownership`() {
        val window = listOf(CostLine("fuel", 100_000))
        val c = CostEngine.costPerKm(window, 200, lines, 2000, depreciationPaise = null)
        assertEquals(500.0, c.fuelPaisePerKm!!, 1e-9)
        assertEquals(1050.0, c.truePaisePerKm!!, 1e-9) // 2,100,000 paise / 2000 km
    }
}
