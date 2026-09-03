package dev.pranav.speed400garage.domain.engine

/** §8.1 — the four rule shapes, plus the conditional one warranty work needs. */
enum class RuleType { TIME_ONLY, DISTANCE_ONLY, WHICHEVER_FIRST, AGE, CONDITIONAL }

/**
 * How loudly this wants attention.
 *
 * Kept separate from [NotifyClass] on purpose: severity is how close the deadline is,
 * class is how the app is allowed to interrupt about it. A light check can be OVERDUE
 * and still only earn a line in the weekly digest.
 */
enum class Severity { INFO, DUE_SOON, DUE, OVERDUE }

/**
 * §8.2 — the notification policy, as data rather than scattered ifs.
 *
 * Under-notifying breaks the promise; over-notifying trains you to swipe them away,
 * which breaks it too. So each class gets its own thresholds and its own channel.
 */
enum class NotifyClass {
    /** 30 / 7 / 1 days before expiry, then daily once expired. */
    DOCUMENT,
    /** 60 / 30 / 7 days, separate high-priority channel (§5.3). */
    WARRANTY,
    /** 1,000 km remaining, then 300 km remaining. */
    ROUTINE,
    /** Chain lube, tyre pressure: a single weekly digest, never individually. */
    DIGEST,
}

/** Where a component's clock last restarted. */
data class Baseline(val epochDay: Long, val odometerKm: Int?)

/** A component as the due engine needs it — decoupled from Room. */
data class DueComponent(
    val key: String,
    val label: String,
    val intervalKm: Int?,
    val intervalDays: Int?,
    val intervalSource: String,
    val manualPageRef: Int?,
    val isWarrantyRelevant: Boolean,
    val isDailyCheck: Boolean,
    val isOneOff: Boolean,
    val firstDueKm: Int?,
)

/** A document with one or two expiry dates. */
data class DueDocument(
    val id: String,
    val label: String,
    val expiresOnDay: Long?,
    /** Indian insurance bundles multi-year TP cover with annual OD cover (§6.1). */
    val secondaryExpiresOnDay: Long?,
    val secondaryLabel: String? = null,
)

/**
 * One thing that wants doing, with everything the UI needs to explain itself.
 *
 * [isProjected] matters: a km-based due date is only as good as the odometer estimate
 * behind it. When that estimate is stale the app says so rather than presenting a
 * confident date it cannot support (§8.3).
 */
data class DueItem(
    val key: String,
    val label: String,
    val ruleType: RuleType,
    val notifyClass: NotifyClass,
    val severity: Severity,
    val dueDay: Long?,
    val dueOdometerKm: Int?,
    val kmRemaining: Int?,
    val daysRemaining: Int?,
    val isProjected: Boolean,
    val isStale: Boolean,
    val intervalSource: String,
    val manualPageRef: Int?,
    val isWarrantyRelevant: Boolean,
)

/**
 * Turns intervals and documents into one sorted list of things that want doing (§8).
 *
 * The engine has to be right or it stops being trusted, and a distrusted reminder
 * system is worse than none — so its whole job is: never claim a date it cannot
 * support, and never let a light check shout.
 */
object DueEngine {

    const val ROUTINE_DUE_SOON_KM = 1_000
    const val ROUTINE_DUE_KM = 300
    val DOCUMENT_THRESHOLD_DAYS = listOf(30, 7, 1)
    val WARRANTY_THRESHOLD_DAYS = listOf(60, 30, 7)

    fun compute(
        components: List<DueComponent>,
        baselines: Map<String, Baseline>,
        bikeStart: Baseline?,
        documents: List<DueDocument>,
        projection: Projection?,
        today: Long,
    ): List<DueItem> {
        val items = components.mapNotNull { component ->
            forComponent(component, baselines[component.key] ?: bikeStart, projection, today)
        } + documents.flatMap { forDocument(it, today) }

        // Nulls last: an item with no computable date is not urgent, it is unknown,
        // and burying it above real deadlines would be its own kind of lie.
        return items.sortedWith(
            compareBy(
                { it.dueDay ?: Long.MAX_VALUE },
                { -it.severity.ordinal },
                { it.label },
            )
        )
    }

    fun forComponent(
        component: DueComponent,
        baseline: Baseline?,
        projection: Projection?,
        today: Long,
    ): DueItem? {
        // Daily pre-ride checks have no meaningful due date — they are simply always
        // wanted. They ride in the weekly digest rather than the due list.
        if (component.isDailyCheck) {
            return DueItem(
                key = component.key,
                label = component.label,
                ruleType = RuleType.TIME_ONLY,
                notifyClass = NotifyClass.DIGEST,
                severity = Severity.INFO,
                dueDay = null,
                dueOdometerKm = null,
                kmRemaining = null,
                daysRemaining = null,
                isProjected = false,
                isStale = false,
                intervalSource = component.intervalSource,
                manualPageRef = component.manualPageRef,
                isWarrantyRelevant = component.isWarrantyRelevant,
            )
        }

        if (component.intervalKm == null && component.intervalDays == null) return null
        if (baseline == null) return null

        val dueOdo = component.intervalKm?.let { interval ->
            val base = baseline.odometerKm ?: return@let null
            if (component.isOneOff) component.firstDueKm ?: interval
            else base + interval
        }
        val timeDueDay = component.intervalDays?.let { baseline.epochDay + it }

        val projectedDay = if (dueOdo != null && projection != null) {
            OdometerProjector.projectedDayFor(dueOdo, projection, today)
        } else null

        val ruleType = when {
            component.intervalKm != null && component.intervalDays != null -> RuleType.WHICHEVER_FIRST
            component.intervalKm != null -> RuleType.DISTANCE_ONLY
            else -> RuleType.TIME_ONLY
        }

        // Whichever comes first — and when the km side has no usable projection, the
        // time side stands alone rather than the item vanishing.
        val effectiveDay = listOfNotNull(projectedDay, timeDueDay).minOrNull()

        val kmRemaining = if (dueOdo != null && projection != null) dueOdo - projection.estimatedKm else null
        val daysRemaining = effectiveDay?.let { (it - today).toInt() }

        val notifyClass = if (component.isWarrantyRelevant) NotifyClass.WARRANTY else NotifyClass.ROUTINE

        return DueItem(
            key = component.key,
            label = component.label,
            ruleType = if (component.isWarrantyRelevant) RuleType.CONDITIONAL else ruleType,
            notifyClass = notifyClass,
            severity = severityFor(notifyClass, daysRemaining, kmRemaining),
            dueDay = effectiveDay,
            dueOdometerKm = dueOdo,
            kmRemaining = kmRemaining,
            daysRemaining = daysRemaining,
            isProjected = projectedDay != null && projectedDay == effectiveDay,
            isStale = projection?.isStale == true && projectedDay != null,
            intervalSource = component.intervalSource,
            manualPageRef = component.manualPageRef,
            isWarrantyRelevant = component.isWarrantyRelevant,
        )
    }

    /** Both expiry dates produce their own item — one field would hide the one that matters. */
    fun forDocument(document: DueDocument, today: Long): List<DueItem> = buildList {
        document.expiresOnDay?.let { add(documentItem(document.id, document.label, it, today)) }
        document.secondaryExpiresOnDay?.let {
            add(documentItem("${document.id}#secondary", document.secondaryLabel ?: "${document.label} (second cover)", it, today))
        }
    }

    private fun documentItem(key: String, label: String, expiryDay: Long, today: Long): DueItem {
        val days = (expiryDay - today).toInt()
        return DueItem(
            key = key,
            label = label,
            ruleType = RuleType.TIME_ONLY,
            notifyClass = NotifyClass.DOCUMENT,
            severity = severityFor(NotifyClass.DOCUMENT, days, null),
            dueDay = expiryDay,
            dueOdometerKm = null,
            kmRemaining = null,
            daysRemaining = days,
            isProjected = false,
            isStale = false,
            intervalSource = "mine",
            manualPageRef = null,
            isWarrantyRelevant = false,
        )
    }

    fun severityFor(notifyClass: NotifyClass, daysRemaining: Int?, kmRemaining: Int?): Severity {
        if (daysRemaining != null && daysRemaining < 0) return Severity.OVERDUE
        if (kmRemaining != null && kmRemaining < 0) return Severity.OVERDUE

        return when (notifyClass) {
            NotifyClass.DOCUMENT -> when {
                daysRemaining == null -> Severity.INFO
                daysRemaining <= DOCUMENT_THRESHOLD_DAYS[2] -> Severity.DUE
                daysRemaining <= DOCUMENT_THRESHOLD_DAYS[1] -> Severity.DUE
                daysRemaining <= DOCUMENT_THRESHOLD_DAYS[0] -> Severity.DUE_SOON
                else -> Severity.INFO
            }
            NotifyClass.WARRANTY -> when {
                daysRemaining != null && daysRemaining <= WARRANTY_THRESHOLD_DAYS[2] -> Severity.DUE
                daysRemaining != null && daysRemaining <= WARRANTY_THRESHOLD_DAYS[1] -> Severity.DUE_SOON
                daysRemaining != null && daysRemaining <= WARRANTY_THRESHOLD_DAYS[0] -> Severity.DUE_SOON
                kmRemaining != null && kmRemaining <= ROUTINE_DUE_KM -> Severity.DUE
                kmRemaining != null && kmRemaining <= ROUTINE_DUE_SOON_KM -> Severity.DUE_SOON
                else -> Severity.INFO
            }
            NotifyClass.ROUTINE -> when {
                kmRemaining != null && kmRemaining <= ROUTINE_DUE_KM -> Severity.DUE
                kmRemaining != null && kmRemaining <= ROUTINE_DUE_SOON_KM -> Severity.DUE_SOON
                daysRemaining != null && daysRemaining <= 7 -> Severity.DUE
                daysRemaining != null && daysRemaining <= 30 -> Severity.DUE_SOON
                else -> Severity.INFO
            }
            NotifyClass.DIGEST -> Severity.INFO
        }
    }

    /**
     * §8.3 — distance reminders rot silently if logging stops. This is what the
     * dashboard nudge and the 30-day notification are driven from.
     */
    fun stalenessNudge(projection: Projection?, today: Long): String? {
        if (projection == null) return "No odometer reading yet — km-based reminders can't work until there is one."
        val days = projection.daysSinceReading
        return when {
            days >= 30 -> "No odometer reading in $days days. Every km-based reminder is guesswork until you log one."
            days > OdometerProjector.STALE_AFTER_DAYS -> "Last reading was $days days ago, so km-based dates are estimates rather than dates."
            else -> null
        }
    }
}
