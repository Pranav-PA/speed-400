package dev.pranav.speed400garage

import dev.pranav.speed400garage.data.seed.ComponentSeedFile
import dev.pranav.speed400garage.data.seed.FactSeedFile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 0's done-when criterion, expressed as tests (§15):
 *
 *   "the app opens, knows the bike exists, and every interval in it traces to a page
 *    number in the handbook"
 *
 * These read the shipped asset files directly, so a future edit that adds an interval
 * without a citation — or quietly fills a gap from general knowledge — fails the build
 * rather than reaching the tablet.
 */
class SeedIntegrityTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val components: ComponentSeedFile by lazy {
        json.decodeFromString(File("src/main/assets/seed/components.json").readText())
    }

    private val facts: FactSeedFile by lazy {
        json.decodeFromString(File("src/main/assets/seed/facts.json").readText())
    }

    // ------------------------------------------------------------------ components

    @Test
    fun `every manual sourced interval cites a handbook page`() {
        val offenders = components.components
            .filter { it.intervalSource == "manual" }
            .filter { it.manualPageRef == null }
        assertTrue(
            "Components claiming a manual interval with no page reference: " +
                offenders.joinToString { it.key },
            offenders.isEmpty(),
        )
    }

    @Test
    fun `no component invents an interval it cannot cite`() {
        // §3 P5: anything the handbook does not state must be marked unverified rather
        // than filled in from general knowledge. An 'unverified' row is therefore only
        // legitimate when it carries NO interval at all.
        val offenders = components.components
            .filter { it.intervalSource != "manual" }
            .filter { it.intervalKm != null || it.intervalDays != null }
        assertTrue(
            "Unverified components asserting an interval anyway: " + offenders.joinToString { it.key },
            offenders.isEmpty(),
        )
    }

    @Test
    fun `component keys are unique`() {
        val keys = components.components.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `action kinds are drawn from the schema vocabulary`() {
        val allowed = setOf("replace", "service", "check", "adjust")
        val offenders = components.components.filterNot { it.actionKind in allowed }
        assertTrue("Unknown action_kind: " + offenders.joinToString { "${it.key}=${it.actionKind}" }, offenders.isEmpty())
    }

    @Test
    fun `the five condition-based items are honestly unverified`() {
        // The handbook gives no replacement interval for these — it inspects them for
        // wear instead. Recording a plausible km figure here would be exactly the
        // failure §3 P5 exists to prevent.
        val expected = setOf("chain", "sprockets", "tyre_front", "tyre_rear", "battery")
        val actual = components.components.filter { it.intervalSource != "manual" }.map { it.key }.toSet()
        assertEquals(expected, actual)
    }

    @Test
    fun `the handbook service interval is 16000 km or annual`() {
        val service = components.components.single { it.key == "scheduled_service" }
        assertEquals(16_000, service.intervalKm)
        assertEquals(365, service.intervalDays)
        assertEquals(116, service.manualPageRef)
    }

    @Test
    fun `the first service is 1000 km or six months`() {
        val first = components.components.single { it.key == "first_service" }
        assertEquals(1_000, first.intervalKm)
        assertEquals(183, first.intervalDays)
        assertTrue(first.oneOff)
    }

    // ------------------------------------------------------------------ facts

    @Test
    fun `every fact cites a handbook page`() {
        val offenders = facts.facts.filter { it.pageRef == null }
        assertTrue("Facts with no page reference: " + offenders.joinToString { it.key }, offenders.isEmpty())
    }

    @Test
    fun `every safety critical fact is manual sourced and cited`() {
        // §10.5 — for these the app either produces a cited value or says it doesn't know.
        val offenders = facts.facts
            .filter { it.isSafetyCritical }
            .filterNot { it.source == "manual" && it.pageRef != null }
        assertTrue("Uncitable safety-critical facts: " + offenders.joinToString { it.key }, offenders.isEmpty())
    }

    @Test
    fun `no fact ships pre-verified`() {
        // §10.3 — rows start unverified and are promoted only by the owner's own eyes
        // on the page. A build must never mark its own extraction as verified.
        val offenders = facts.facts.filter { it.verifiedOn != null }
        assertTrue("Facts shipped as verified: " + offenders.joinToString { it.key }, offenders.isEmpty())
    }

    @Test
    fun `fact keys are unique`() {
        val keys = facts.facts.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `page references fall inside the handbook`() {
        val pages = 229
        val offenders = (facts.facts.mapNotNull { it.pageRef } + components.components.mapNotNull { it.manualPageRef })
            .filter { it !in 1..pages }
        assertTrue("Page refs outside 1..$pages: $offenders", offenders.isEmpty())
    }

    @Test
    fun `both seed files cite the same source document`() {
        assertEquals(components.sourceDocument.partNumber, facts.sourceDocument.partNumber)
        assertEquals("3850838-IN", facts.sourceDocument.partNumber)
        assertNotNull(facts.sourceDocument.retrievedFrom)
    }

    // ---------------------------------------------- spot checks on safety-critical values

    @Test
    fun `tyre pressures match the Speed 400 column`() {
        // Handbook p.201. One figure each — there is no separate pillion pressure for
        // this model, and the app must not imply that there is.
        val front = facts.facts.single { it.key == "tyre.front_pressure" }
        val rear = facts.facts.single { it.key == "tyre.rear_pressure" }
        assertTrue(front.value.contains("1.79 bar") && front.value.contains("26.0 psi"))
        assertTrue(rear.value.contains("2.28 bar") && rear.value.contains("33.0 psi"))
        assertEquals(201, front.pageRef)
        assertEquals(201, rear.pageRef)
    }

    @Test
    fun `chain slack is the Speed 400 figure and not the Scrambler one`() {
        // 20-25 mm for the Speed 400; the Scrambler models are 40-45 mm. Reading the
        // wrong row of a five-model handbook is the likeliest way to ship a wrong number.
        val slack = facts.facts.single { it.key == "chain.free_movement" }
        assertEquals("20-25", slack.value)
        assertEquals(134, slack.pageRef)
        assertTrue(slack.notes.orEmpty().contains("40-45"))
    }

    @Test
    fun `displacement is 398cc not 349cc`() {
        // §3 P4 cites this specifically: a spec aggregator had it as 349cc. It is not.
        val displacement = facts.facts.single { it.key == "engine.displacement" }
        assertEquals("398", displacement.value)
        assertEquals("cc", displacement.unit)
    }

    @Test
    fun `torque figures are present`() {
        // The plan assumed the owner's handbook carried no torque specs and that
        // refusing torque questions was therefore always correct. This edition has a
        // Torque Figures table on p.202, so that assumption no longer holds.
        val torques = facts.facts.filter { it.category == "torque" }
        assertTrue("Expected torque figures from p.202", torques.size >= 11)
        assertTrue(torques.all { it.pageRef == 202 })
        assertTrue(torques.all { it.isSafetyCritical })
    }

    @Test
    fun `warranty period is recorded as not stated rather than guessed`() {
        val warranty = facts.facts.single { it.key == "warranty.period" }
        assertTrue(warranty.value.contains("NOT STATED"))
        assertFalse(warranty.value.contains("2 year"))
    }
}
