package dev.pranav.speed400garage

import dev.pranav.speed400garage.domain.Provenance
import dev.pranav.speed400garage.domain.SafetyRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvenanceTest {

    @Test
    fun `known sources map to their badges`() {
        assertEquals(Provenance.MANUAL, Provenance.fromSource("manual"))
        assertEquals(Provenance.MY_RECORDS, Provenance.fromSource("mine"))
        assertEquals(Provenance.ESTIMATE, Provenance.fromSource("estimate"))
    }

    @Test
    fun `an unknown source falls to general rather than up to manual`() {
        assertEquals(Provenance.GENERAL, Provenance.fromSource("dealer"))
        assertEquals(Provenance.GENERAL, Provenance.fromSource("community"))
        assertEquals(Provenance.GENERAL, Provenance.fromSource("unverified"))
        assertEquals(Provenance.GENERAL, Provenance.fromSource(null))
    }

    @Test
    fun `a safety critical value needs both a manual source and a page`() {
        assertTrue(SafetyRule.isCitable(Provenance.MANUAL, pageRef = 201, isSafetyCritical = true))
        assertFalse(SafetyRule.isCitable(Provenance.MANUAL, pageRef = null, isSafetyCritical = true))
        assertFalse(SafetyRule.isCitable(Provenance.GENERAL, pageRef = 201, isSafetyCritical = true))
        assertFalse(SafetyRule.isCitable(Provenance.ESTIMATE, pageRef = 201, isSafetyCritical = true))
    }

    @Test
    fun `a non safety value is shown whatever its provenance`() {
        assertTrue(SafetyRule.isCitable(Provenance.GENERAL, pageRef = null, isSafetyCritical = false))
    }
}
