package dev.pranav.speed400garage.ai

import dev.pranav.speed400garage.domain.Provenance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroundingTest {

    @Test
    fun `numbers are pulled out of an answer`() {
        val found = GroundingCheck.numbersIn("You've spent ₹18,430 on fuel across 34 fills.")
        assertTrue(found.contains("18430"))
        assertTrue(found.contains("34"))
    }

    @Test
    fun `part specifications are not mistaken for figures`() {
        // 10W/50, VR6NEU and ZR17 are identifiers, not quantities. Flagging them
        // would make the check fire on every oil-grade answer and get ignored.
        val found = GroundingCheck.numbersIn("Fully synthetic 10W/50 meeting API SN, plug Bosch VR6NEU, 110/70 ZR17")
        assertFalse("10W/50 is a grade", found.contains("10"))
        assertFalse("VR6NEU is a part number", found.contains("6"))
    }

    @Test
    fun `an answer whose numbers all come from sources passes`() {
        val verdict = GroundingCheck.check(
            answer = "You're averaging 32.4 km/l over the last 5 full tanks.",
            sources = listOf("32.4 km/l", "5"),
            isSafetyCritical = false,
            provenance = Provenance.MY_RECORDS,
        )
        assertTrue(verdict is GroundingCheck.Verdict.Ok)
    }

    @Test
    fun `an invented figure in a safety answer is BLOCKED, not softened`() {
        // The whole reason §10.4 exists: a plausible-looking torque figure strips a
        // caliper bolt. There is no middle option for these.
        val verdict = GroundingCheck.check(
            answer = "Torque the oil drain plug to 25 Nm.",
            sources = listOf("13 Nm"),
            isSafetyCritical = true,
            provenance = Provenance.MANUAL,
        )
        assertTrue(verdict is GroundingCheck.Verdict.Blocked)
        assertEquals(listOf("25"), (verdict as GroundingCheck.Verdict.Blocked).numbers)
    }

    @Test
    fun `an invented figure in a non-safety answer is downgraded and still shown`() {
        val verdict = GroundingCheck.check(
            answer = "That service usually runs about 4500 rupees.",
            sources = listOf("no figures here"),
            isSafetyCritical = false,
            provenance = Provenance.MY_RECORDS,
        )
        assertTrue(verdict is GroundingCheck.Verdict.Downgraded)
        assertTrue((verdict as GroundingCheck.Verdict.Downgraded).numbers.contains("4500"))
    }

    @Test
    fun `comma formatting does not defeat the check`() {
        val verdict = GroundingCheck.check(
            answer = "You've spent ₹18,430 on fuel.",
            sources = listOf("₹18,430"),
            isSafetyCritical = false,
            provenance = Provenance.MY_RECORDS,
        )
        assertTrue(verdict is GroundingCheck.Verdict.Ok)
    }

    @Test
    fun `small counts in ordinary phrasing are not flagged as data`() {
        val verdict = GroundingCheck.check(
            answer = "There are 2 things due.",
            sources = emptyList(),
            isSafetyCritical = false,
            provenance = Provenance.MY_RECORDS,
        )
        assertTrue(verdict is GroundingCheck.Verdict.Ok)
    }
}

class SafetyTopicsTest {

    @Test
    fun `the safety critical topics are recognised`() {
        listOf(
            "what's the rear tyre pressure",
            "torque for the oil drain plug",
            "how much oil does it take",
            "what coolant does it use",
            "minimum brake pad thickness",
            "valve clearance spec",
            "what's the payload limit",
            "front tyre size",
        ).forEach { assertTrue(it, SafetyTopics.isSafetyCritical(it)) }
    }

    @Test
    fun `ordinary questions are not treated as safety critical`() {
        listOf(
            "how much have I spent on fuel",
            "when did I last service it",
            "what's my mileage",
            "is that rattle normal",
        ).forEach { assertFalse(it, SafetyTopics.isSafetyCritical(it)) }
    }

    @Test
    fun `the refusal never hedges`() {
        // §10.5: never estimates, never says "typically around", never reasons from
        // other motorcycles.
        val refusal = SafetyTopics.REFUSAL.lowercase()
        assertFalse(refusal.contains("approximately"))
        assertFalse(refusal.contains("typically"))
        assertFalse(refusal.contains("around"))
        assertTrue(refusal.contains("won't guess"))
    }
}
