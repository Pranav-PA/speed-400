package dev.pranav.speed400garage.ai

import dev.pranav.speed400garage.domain.Provenance

/**
 * §10.4 — the numeric grounding check.
 *
 * After an answer is composed, every numeral in it is extracted and checked against
 * the numerals that appeared in the tool results and cited manual text. Anything the
 * app cannot account for is a number the model produced from nowhere.
 *
 * This is a deterministic post-check, NOT a prompt instruction. Prompts alone will not
 * reliably stop a model producing a plausible-looking torque figure, and "plausible
 * torque figure" is precisely the failure that strips a caliper bolt.
 */
object GroundingCheck {

    /**
     * Matches numbers the way they appear in answers: 1,234.5 · 16000 · 2.28 · 0.9
     * Deliberately ignores numbers glued to letters (10W/50, VR6NEU, ZR17) — those are
     * part specifications, not quantities, and treating them as figures produces false
     * alarms on every oil-grade answer.
     */
    private val NUMBER = Regex("""(?<![A-Za-z0-9./-])\d[\d,]*(?:\.\d+)?(?![A-Za-z0-9]|\.\d|/)""")

    fun numbersIn(text: String): List<String> =
        NUMBER.findAll(text).map { it.value.replace(",", "").trimEnd('.') }.toList()

    /**
     * @param answer the composed answer.
     * @param sources every tool result and cited chunk that fed it.
     * @return the numerals present in [answer] that appear in none of [sources].
     */
    fun ungrounded(answer: String, sources: List<String>): List<String> {
        val allowed = sources.flatMap { numbersIn(it) }.toSet() + ALWAYS_ALLOWED
        return numbersIn(answer).filter { it !in allowed }.distinct()
    }

    /**
     * The verdict on an answer.
     *
     * A safety-critical answer with an ungrounded number is BLOCKED, not downgraded —
     * §10.5 leaves no middle option for tyre pressures, torque, fluid specs, brake
     * specs or valve clearances. Everything else is downgraded to ⚪ and shown, since
     * a hedged answer about DIY procedure is still useful.
     */
    fun check(
        answer: String,
        sources: List<String>,
        isSafetyCritical: Boolean,
        provenance: Provenance,
    ): Verdict {
        val loose = ungrounded(answer, sources)
        return when {
            loose.isEmpty() -> Verdict.Ok(provenance)
            isSafetyCritical -> Verdict.Blocked(loose)
            else -> Verdict.Downgraded(loose)
        }
    }

    sealed interface Verdict {
        data class Ok(val provenance: Provenance) : Verdict
        /** Refused. The answer never reaches the screen. */
        data class Blocked(val numbers: List<String>) : Verdict
        /** Shown, but as ⚪ General with the unaccounted figures named. */
        data class Downgraded(val numbers: List<String>) : Verdict
    }

    /**
     * Years, and small counts that show up in ordinary phrasing ("2 of them",
     * "the 1st service"). Allowing these avoids flagging language as if it were data,
     * which would make the check noisy enough to be ignored.
     */
    private val ALWAYS_ALLOWED = setOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
}

/**
 * §10.5 — the safety rule.
 *
 * For these topics the assistant answers only from the fact table or a cited manual
 * chunk. If neither has it, it says so. It never estimates, never says "typically
 * around", and never reasons from other motorcycles.
 */
object SafetyTopics {

    private val KEYWORDS = mapOf(
        "tyre pressure" to listOf("tyre pressure", "tire pressure", "psi", "bar cold", "inflation"),
        "torque" to listOf("torque", "nm", "tighten", "how tight"),
        "fluid" to listOf("oil grade", "oil capacity", "coolant", "brake fluid", "fluid spec", "how much oil"),
        "brakes" to listOf("brake pad", "pad thickness", "disc thickness", "minimum thickness"),
        "valve" to listOf("valve clearance", "tappet"),
        "electrical" to listOf("battery rating", "fuse rating", "alternator", "amp"),
        "load" to listOf("payload", "load limit", "how much weight", "carrying capacity"),
        "tyre spec" to listOf("tyre size", "tire size", "speed rating", "tread depth"),
    )

    fun isSafetyCritical(question: String): Boolean {
        val q = question.lowercase()
        return KEYWORDS.values.any { list -> list.any { q.contains(it) } }
    }

    fun topicOf(question: String): String? {
        val q = question.lowercase()
        return KEYWORDS.entries.firstOrNull { (_, list) -> list.any { q.contains(it) } }?.key
    }

    const val REFUSAL =
        "Not in the manual I've indexed — check the printed handbook or ask the dealer. " +
            "I won't guess at this one."
}
