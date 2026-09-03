package dev.pranav.speed400garage.engine

import dev.pranav.speed400garage.domain.engine.Baseline
import dev.pranav.speed400garage.domain.engine.DueComponent
import dev.pranav.speed400garage.domain.engine.DueDocument
import dev.pranav.speed400garage.domain.engine.DueEngine
import dev.pranav.speed400garage.domain.engine.NotifyClass
import dev.pranav.speed400garage.domain.engine.OdometerProjector
import dev.pranav.speed400garage.domain.engine.Reading
import dev.pranav.speed400garage.domain.engine.RuleType
import dev.pranav.speed400garage.domain.engine.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DueEngineTest {

    private val today = 1000L

    /** 30 km/day, last read today at 10,000 km. */
    private val projection = OdometerProjector.project(
        (0..4).map { Reading(today - 40 + it * 10L, 8_800 + it * 300) },
        today,
    )!!

    private fun component(
        key: String = "engine_oil",
        km: Int? = null,
        days: Int? = null,
        warranty: Boolean = false,
        daily: Boolean = false,
        oneOff: Boolean = false,
        firstDueKm: Int? = null,
    ) = DueComponent(
        key = key, label = key, intervalKm = km, intervalDays = days,
        intervalSource = "manual", manualPageRef = 116,
        isWarrantyRelevant = warranty, isDailyCheck = daily,
        isOneOff = oneOff, firstDueKm = firstDueKm,
    )

    @Test
    fun `a distance rule becomes a date via the projection`() {
        val item = DueEngine.forComponent(
            component(km = 16_000),
            Baseline(today - 100, 9_000),
            projection, today,
        )!!
        assertEquals(RuleType.DISTANCE_ONLY, item.ruleType)
        assertEquals(25_000, item.dueOdometerKm)
        assertNotNull("a km rule must land on a day so a notification can fire", item.dueDay)
        assertTrue(item.isProjected)
    }

    @Test
    fun `whichever comes first takes the earlier of the two`() {
        // Time is due in 10 days; distance is ~500 days away at 30 km/day.
        val item = DueEngine.forComponent(
            component(km = 16_000, days = 365),
            Baseline(today - 355, 9_000),
            projection, today,
        )!!
        assertEquals(RuleType.WHICHEVER_FIRST, item.ruleType)
        assertEquals(today + 10, item.dueDay)
        assertFalse("the time side won, so this is not a projected date", item.isProjected)
    }

    @Test
    fun `a km rule with no usable projection still reports its time side`() {
        val single = OdometerProjector.project(listOf(Reading(today - 5, 10_000)), today)!!
        val item = DueEngine.forComponent(
            component(km = 16_000, days = 365),
            Baseline(today - 300, 9_000),
            single, today,
        )!!
        assertEquals("the item must not vanish just because km can't be projected", today + 65, item.dueDay)
        assertFalse(item.isProjected)
    }

    @Test
    fun `a stale projection is flagged rather than presented as a date`() {
        val stale = OdometerProjector.project(
            listOf(Reading(today - 60, 9_000), Reading(today - 40, 9_600)),
            today,
        )!!
        assertTrue(stale.isStale)
        val item = DueEngine.forComponent(component(km = 16_000), Baseline(today - 100, 9_000), stale, today)!!
        assertTrue("a date built on a stale estimate must say so", item.isStale)
    }

    @Test
    fun `routine severity escalates at 1000 then 300 km remaining`() {
        assertEquals(Severity.INFO, DueEngine.severityFor(NotifyClass.ROUTINE, null, 2_000))
        assertEquals(Severity.DUE_SOON, DueEngine.severityFor(NotifyClass.ROUTINE, null, 900))
        assertEquals(Severity.DUE, DueEngine.severityFor(NotifyClass.ROUTINE, null, 200))
        assertEquals(Severity.OVERDUE, DueEngine.severityFor(NotifyClass.ROUTINE, null, -50))
    }

    @Test
    fun `documents escalate at 30 then 7 then expiry`() {
        assertEquals(Severity.INFO, DueEngine.severityFor(NotifyClass.DOCUMENT, 60, null))
        assertEquals(Severity.DUE_SOON, DueEngine.severityFor(NotifyClass.DOCUMENT, 25, null))
        assertEquals(Severity.DUE, DueEngine.severityFor(NotifyClass.DOCUMENT, 5, null))
        assertEquals(Severity.OVERDUE, DueEngine.severityFor(NotifyClass.DOCUMENT, -1, null))
    }

    @Test
    fun `warranty work escalates earlier than routine work`() {
        // 45 days out: warranty is already DUE_SOON, routine is not.
        assertEquals(Severity.DUE_SOON, DueEngine.severityFor(NotifyClass.WARRANTY, 45, null))
        assertEquals(Severity.INFO, DueEngine.severityFor(NotifyClass.ROUTINE, 45, null))
    }

    @Test
    fun `a warranty relevant component gets the loud class and the conditional rule`() {
        val item = DueEngine.forComponent(
            component(km = 16_000, days = 365, warranty = true),
            Baseline(today - 100, 9_000), projection, today,
        )!!
        assertEquals(NotifyClass.WARRANTY, item.notifyClass)
        assertEquals(RuleType.CONDITIONAL, item.ruleType)
    }

    @Test
    fun `a daily check never shouts and never claims a date`() {
        val item = DueEngine.forComponent(
            component(key = "tyre_pressure_check", days = 1, daily = true),
            Baseline(today - 100, 9_000), projection, today,
        )!!
        assertEquals(NotifyClass.DIGEST, item.notifyClass)
        assertEquals(Severity.INFO, item.severity)
        assertNull(item.dueDay)
    }

    @Test
    fun `a component with no interval produces nothing at all`() {
        // The five condition-based items from the handbook — chain, sprockets, tyres,
        // battery. Inventing a due date for them is exactly what P5 forbids.
        assertNull(DueEngine.forComponent(component(key = "battery"), Baseline(today - 100, 9_000), projection, today))
    }

    @Test
    fun `a one-off item is due at its own odometer, not baseline plus interval`() {
        val item = DueEngine.forComponent(
            component(key = "coolant_pump_seals", km = 48_000, oneOff = true, firstDueKm = 48_000),
            Baseline(today - 100, 9_000), projection, today,
        )!!
        assertEquals(48_000, item.dueOdometerKm)
    }

    @Test
    fun `both insurance expiry dates produce their own reminder`() {
        // Indian new-vehicle insurance bundles multi-year TP with annual OD cover.
        // One field would silently hide whichever expires first.
        val items = DueEngine.forDocument(
            DueDocument("doc1", "Insurance (OD)", today + 20, today + 700, "Insurance (TP)"),
            today,
        )
        assertEquals(2, items.size)
        assertEquals(Severity.DUE_SOON, items[0].severity)
        assertEquals(Severity.INFO, items[1].severity)
        assertTrue(items.all { it.notifyClass == NotifyClass.DOCUMENT })
    }

    @Test
    fun `the list sorts by date with undated items last`() {
        val list = DueEngine.compute(
            components = listOf(
                component(key = "far", km = 16_000),
                component(key = "soon", days = 10),
                component(key = "daily_check", days = 1, daily = true),
            ),
            baselines = mapOf(
                "far" to Baseline(today, 10_000),
                "soon" to Baseline(today, 10_000),
                "daily_check" to Baseline(today, 10_000),
            ),
            bikeStart = null,
            documents = emptyList(),
            projection = projection,
            today = today,
        )
        assertEquals("soon", list.first().key)
        assertEquals("a dateless item is unknown, not urgent", "daily_check", list.last().key)
    }

    @Test
    fun `a component never serviced falls back to the bike's start`() {
        val list = DueEngine.compute(
            components = listOf(component(key = "engine_oil", km = 16_000, days = 365)),
            baselines = emptyMap(),
            bikeStart = Baseline(today - 200, 0),
            documents = emptyList(),
            projection = projection,
            today = today,
        )
        assertEquals(1, list.size)
        assertEquals(16_000, list.single().dueOdometerKm)
    }

    @Test
    fun `staleness nudges get louder as the odometer ages`() {
        assertNull(DueEngine.stalenessNudge(projection, today))

        val twoWeeks = OdometerProjector.project(
            listOf(Reading(today - 40, 9_000), Reading(today - 20, 9_600)), today,
        )!!
        assertTrue(DueEngine.stalenessNudge(twoWeeks, today)!!.contains("estimates"))

        val month = OdometerProjector.project(
            listOf(Reading(today - 60, 9_000), Reading(today - 35, 9_600)), today,
        )!!
        assertTrue(DueEngine.stalenessNudge(month, today)!!.contains("guesswork"))

        assertNotNull(DueEngine.stalenessNudge(null, today))
    }
}
