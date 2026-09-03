package dev.pranav.speed400garage.domain

/**
 * Plan §3 P4 — provenance is a first-class field. Every number the app shows carries
 * one of these, and the badge is rendered wherever the number is.
 *
 * The ordering matters: [MANUAL] outranks everything, [GENERAL] is the floor. A
 * maintenance interval sourced from a forum post must never look identical to one
 * that traces to a page of the owner's handbook.
 */
enum class Provenance(val badge: String, val label: String) {
    /** From the Speed 400 owner's handbook, with a page citation. */
    MANUAL("🟢", "Manual"),

    /** Computed from Pranav's own logged data. */
    MY_RECORDS("🔵", "My records"),

    /** Derived or projected by the app — e.g. today's odometer. */
    ESTIMATE("🟡", "Estimate"),

    /** Unverified general knowledge or a community rule-of-thumb. */
    GENERAL("⚪", "General");

    companion object {
        /**
         * Maps a stored `interval_source` / `source` string onto a badge.
         *
         * Anything unrecognised falls to [GENERAL] rather than up to [MANUAL]:
         * an unknown source is by definition not a cited one.
         */
        fun fromSource(source: String?): Provenance = when (source?.lowercase()) {
            "manual" -> MANUAL
            "mine", "my_records" -> MY_RECORDS
            "estimate", "derived" -> ESTIMATE
            else -> GENERAL
        }
    }
}

/**
 * Plan §3 P5 — the app either produces a cited value for a safety-critical fact or
 * says it does not know. There is no middle option and no "approximately".
 */
object SafetyRule {
    /**
     * True when [fact] may be shown as an answer to a safety-critical question.
     *
     * A safety-critical fact needs both [Provenance.MANUAL] and a page number. Without
     * the page there is nothing to check the value against, so it does not qualify.
     */
    fun isCitable(provenance: Provenance, pageRef: Int?, isSafetyCritical: Boolean): Boolean =
        if (isSafetyCritical) provenance == Provenance.MANUAL && pageRef != null else true

    const val REFUSAL = "Not in the manual I've indexed — check the printed manual or the dealer."
}
