package dev.pranav.speed400garage.ai

import dev.pranav.speed400garage.data.db.GarageDatabase
import dev.pranav.speed400garage.data.repo.GarageRepository
import dev.pranav.speed400garage.domain.Provenance
import dev.pranav.speed400garage.domain.engine.DueComponent
import dev.pranav.speed400garage.domain.engine.DueDocument
import dev.pranav.speed400garage.domain.engine.DueEngine
import dev.pranav.speed400garage.domain.engine.Fill
import dev.pranav.speed400garage.domain.engine.FillType
import dev.pranav.speed400garage.domain.engine.FuelEconomyCalculator
import dev.pranav.speed400garage.domain.engine.OdometerProjector
import dev.pranav.speed400garage.domain.engine.Severity
import dev.pranav.speed400garage.ui.log.Fmt
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One answer, composed entirely on the device.
 *
 * [sources] is what the grounding check (§10.4) verifies the answer's numerals
 * against, so every figure in [text] must have come from one of them.
 */
data class ToolAnswer(
    val text: String,
    val provenance: Provenance,
    val pageRef: Int? = null,
    val sources: List<String>,
    val isSafetyCritical: Boolean = false,
)

/**
 * Executes the router's chosen tool against SQLite and renders the sentence from a
 * local template (§10.6).
 *
 * This is the half of the assistant that never touches the network. For the large
 * majority of record questions the numbers never leave the tablet — only the question
 * text and a static tool schema do. It is also why the numbers cannot be garbled: a
 * template cannot hallucinate a total the way a language model can.
 */
@Singleton
class ToolExecutor @Inject constructor(
    private val db: GarageDatabase,
    private val repo: GarageRepository,
) {

    suspend fun execute(call: ToolCall): ToolAnswer {
        val bikeId = repo.activeBikeId()
            ?: return ToolAnswer("There's no bike on file yet.", Provenance.MY_RECORDS, sources = emptyList())

        return when (call.name) {
            Tools.SUM_EXPENSES -> sumExpenses(bikeId, call)
            Tools.LAST_EVENT -> lastEvent(bikeId, call)
            Tools.KM_SINCE -> kmSince(bikeId, call)
            Tools.FUEL_ECONOMY -> fuelEconomy(bikeId)
            Tools.CURRENT_ODOMETER -> currentOdometer(bikeId)
            Tools.DUE_ITEMS -> dueItems(bikeId)
            Tools.SERVICE_HISTORY -> serviceHistory(bikeId, call)
            Tools.FIND_DOCUMENTS -> findDocuments(call)
            Tools.SEARCH_NOTES -> searchNotes(bikeId, call)
            Tools.SPEC_LOOKUP -> specLookup(call)
            else -> ToolAnswer(
                "I don't have a way to answer that from your records.",
                Provenance.GENERAL, sources = emptyList(),
            )
        }
    }

    // ------------------------------------------------------------------ record tools

    private suspend fun sumExpenses(bikeId: String, call: ToolCall): ToolAnswer {
        val category = call.args["category"]?.lowercase()?.takeIf { it.isNotBlank() }
        val from = call.args["from"]?.let(::parseDay)
        val to = call.args["to"]?.let(::parseDay) ?: repo.today()
        val fromMillis = from?.let { repo.millisOf(it) } ?: 0L
        val toMillis = repo.millisOf(to) + DAY_MILLIS - 1

        val total = if (category != null) {
            db.costDao().let { _ -> db.lineItemDao().totalPaiseByCategoryBetween(bikeId, category, fromMillis, toMillis) }
        } else {
            db.lineItemDao().totalPaiseBetween(bikeId, fromMillis, toMillis)
        }
        val count = db.lineItemDao().countBetween(bikeId, fromMillis, toMillis, category)

        val what = category?.let { " on $it" } ?: ""
        val window = if (from != null) " between ${Fmt.date(from)} and ${Fmt.date(to)}" else " in total"
        val amount = Fmt.rupees(total)
        return ToolAnswer(
            text = "You've spent $amount$what$window, across $count ${if (count == 1) "entry" else "entries"}.",
            provenance = Provenance.MY_RECORDS,
            sources = listOf(amount, count.toString(), from?.let { Fmt.date(it) }.orEmpty(), Fmt.date(to)),
        )
    }

    private suspend fun lastEvent(bikeId: String, call: ToolCall): ToolAnswer {
        val componentKey = call.args["component_key"]?.takeIf { it.isNotBlank() }
        if (componentKey != null) {
            val action = db.componentActionDao().lastActions(bikeId).firstOrNull { it.componentKey == componentKey }
            val component = db.componentDao().byKey(componentKey)
            val name = component?.displayName ?: componentKey
            if (action?.lastActionAt == null) {
                return ToolAnswer(
                    "You haven't logged $name being done — so I don't know when it last was.",
                    Provenance.MY_RECORDS, sources = emptyList(),
                )
            }
            val day = Fmt.dateMillis(action.lastActionAt)
            val odo = action.lastActionOdometerKm?.let { Fmt.km(it) }
            return ToolAnswer(
                text = "$name was last done on $day" + (odo?.let { ", at $it" } ?: "") + ".",
                provenance = Provenance.MY_RECORDS,
                sources = listOfNotNull(day, odo),
            )
        }

        val type = call.args["event_type"]?.takeIf { it.isNotBlank() }
            ?: return ToolAnswer("I need to know what you're asking about.", Provenance.MY_RECORDS, sources = emptyList())
        val event = db.eventDao().lastOfType(bikeId, type)
            ?: return ToolAnswer("Nothing of that kind is logged yet.", Provenance.MY_RECORDS, sources = emptyList())
        val day = Fmt.dateMillis(event.occurredAt)
        val odo = event.odometerKm?.let { Fmt.km(it) }
        return ToolAnswer(
            text = "The last one was \"${event.title}\" on $day" + (odo?.let { ", at $it" } ?: "") + ".",
            provenance = Provenance.MY_RECORDS,
            sources = listOfNotNull(day, odo),
        )
    }

    private suspend fun kmSince(bikeId: String, call: ToolCall): ToolAnswer {
        val key = call.args["component_key"].orEmpty()
        val action = db.componentActionDao().lastActions(bikeId).firstOrNull { it.componentKey == key }
        val component = db.componentDao().byKey(key)
        val name = component?.displayName ?: key
        val since = action?.lastActionOdometerKm
            ?: return ToolAnswer(
                "You haven't logged $name being done at a known odometer, so I can't count from it.",
                Provenance.MY_RECORDS, sources = emptyList(),
            )
        val projection = OdometerProjector.project(repo.readings(bikeId), repo.today())
            ?: return ToolAnswer("No odometer readings yet.", Provenance.MY_RECORDS, sources = emptyList())

        val km = Fmt.km(projection.estimatedKm - since)
        val interval = component?.intervalKm?.let { Fmt.km(it) }
        return ToolAnswer(
            text = "About $km since $name was last done" +
                (interval?.let { ", against an interval of $it" } ?: "") +
                if (projection.isStale) ". That's off a stale odometer estimate, so treat it loosely." else ".",
            // Amber, not blue: it rests on a projection, not an observation.
            provenance = Provenance.ESTIMATE,
            pageRef = component?.manualPageRef,
            sources = listOfNotNull(km, interval),
        )
    }

    private suspend fun fuelEconomy(bikeId: String): ToolAnswer {
        val report = FuelEconomyCalculator.report(
            db.fuelDao().fillsFor(bikeId).map {
                Fill(
                    id = it.eventId, epochDay = repo.epochDayOf(it.occurredAt), odometerKm = it.odometerKm,
                    litres = it.litres,
                    fillType = runCatching { FillType.valueOf(it.fillType.uppercase()) }.getOrDefault(FillType.FULL),
                    missedPrevious = it.missedPrevious, amountPaise = it.amountPaise,
                )
            }
        )
        val rolling = report.rollingKmpl
            ?: return ToolAnswer(
                "Not enough full tanks logged yet — mileage needs two full-to-full fills before it means anything.",
                Provenance.MY_RECORDS, sources = emptyList(),
            )
        val rollingText = Fmt.kmpl(rolling)
        val latestText = Fmt.kmpl(report.latestKmpl)
        val n = minOf(report.spans.size, report.rollingWindow).toString()
        return ToolAnswer(
            text = "You're averaging $rollingText over the last $n full tanks. The most recent tank was $latestText.",
            provenance = Provenance.MY_RECORDS,
            sources = listOf(rollingText, latestText, n),
        )
    }

    private suspend fun currentOdometer(bikeId: String): ToolAnswer {
        val projection = OdometerProjector.project(repo.readings(bikeId), repo.today())
            ?: return ToolAnswer("No odometer reading logged yet.", Provenance.MY_RECORDS, sources = emptyList())
        val estimate = Fmt.km(projection.estimatedKm)
        val last = Fmt.km(projection.lastReading.km)
        val days = projection.daysSinceReading.toString()
        return ToolAnswer(
            text = "About $estimate. Last actually read at $last, $days days ago." +
                if (projection.isStale) " That estimate is stale — worth logging a reading." else "",
            provenance = if (projection.hasRate) Provenance.ESTIMATE else Provenance.MY_RECORDS,
            sources = listOf(estimate, last, days),
        )
    }

    private suspend fun dueItems(bikeId: String): ToolAnswer {
        val projection = OdometerProjector.project(repo.readings(bikeId), repo.today())
        val items = DueEngine.compute(
            components = db.componentDao().allOnce().map {
                DueComponent(
                    it.key, it.displayName, it.intervalKm, it.intervalDays, it.intervalSource,
                    it.manualPageRef, it.isWarrantyRelevant, it.isDailyCheck, it.isOneOff, it.firstDueKm,
                )
            },
            baselines = repo.baselines(bikeId),
            bikeStart = repo.bikeStart(bikeId),
            documents = db.documentDao().all().map {
                DueDocument(
                    it.id, it.docType.replaceFirstChar { c -> c.uppercase() },
                    it.expiresOn?.let { e -> repo.epochDayOf(e) },
                    it.secondaryExpiresOn?.let { e -> repo.epochDayOf(e) },
                )
            },
            projection = projection,
            today = repo.today(),
        ).filter { it.severity >= Severity.DUE_SOON }

        if (items.isEmpty()) {
            return ToolAnswer("Nothing's due or overdue right now.", Provenance.MY_RECORDS, sources = emptyList())
        }
        val lines = items.take(5).map { item ->
            val when_ = item.kmRemaining?.let { Fmt.km(it) } ?: item.daysRemaining?.let { "$it days" } ?: "soon"
            if (item.severity == Severity.OVERDUE) "${item.label} — overdue" else "${item.label} — $when_ away"
        }
        return ToolAnswer(
            text = "You've got ${items.size} thing${if (items.size == 1) "" else "s"} wanting attention:\n" +
                lines.joinToString("\n") { "· $it" },
            provenance = Provenance.MY_RECORDS,
            sources = lines + items.size.toString(),
        )
    }

    private suspend fun serviceHistory(bikeId: String, call: ToolCall): ToolAnswer {
        val limit = call.args["limit"]?.toIntOrNull() ?: 10
        val events = db.eventDao().recentOfTypes(bikeId, listOf("service", "repair"), limit)
        if (events.isEmpty()) {
            return ToolAnswer("No services logged yet.", Provenance.MY_RECORDS, sources = emptyList())
        }
        val lines = events.map {
            "${Fmt.dateMillis(it.occurredAt)} — ${it.title}" + (it.odometerKm?.let { km -> " at ${Fmt.km(km)}" } ?: "")
        }
        return ToolAnswer(
            text = "The last ${events.size}:\n" + lines.joinToString("\n") { "· $it" },
            provenance = Provenance.MY_RECORDS,
            sources = lines + events.size.toString(),
        )
    }

    private suspend fun findDocuments(call: ToolCall): ToolAnswer {
        val type = call.args["doc_type"]?.lowercase()?.takeIf { it.isNotBlank() }
        val documents = db.documentDao().all().filter { type == null || it.docType == type }
        if (documents.isEmpty()) {
            return ToolAnswer("No documents on file.", Provenance.MY_RECORDS, sources = emptyList())
        }
        val lines = documents.map { doc ->
            val expiry = doc.expiresOn?.let { Fmt.dateMillis(it) } ?: "no expiry recorded"
            "${doc.docType.replaceFirstChar { it.uppercase() }} — $expiry"
        }
        return ToolAnswer(
            text = lines.joinToString("\n") { "· $it" },
            provenance = Provenance.MY_RECORDS,
            sources = lines,
        )
    }

    private suspend fun searchNotes(bikeId: String, call: ToolCall): ToolAnswer {
        val text = call.args["text"].orEmpty().trim()
        if (text.isBlank()) return ToolAnswer("What should I look for?", Provenance.MY_RECORDS, sources = emptyList())
        val hits = db.searchDao().searchLike(bikeId, text)
        if (hits.isEmpty()) {
            return ToolAnswer("Nothing in your log mentions \"$text\".", Provenance.MY_RECORDS, sources = emptyList())
        }
        val lines = hits.take(6).map { "${Fmt.dateMillis(it.occurredAt)} — ${it.title}" }
        return ToolAnswer(
            text = "${hits.size} match${if (hits.size == 1) "" else "es"}:\n" + lines.joinToString("\n") { "· $it" },
            provenance = Provenance.MY_RECORDS,
            sources = lines + hits.size.toString(),
        )
    }

    // ------------------------------------------------------------------ knowledge tool

    /**
     * The curated fact table (§10.3), which is the authority for §10.5's safety rule.
     *
     * Offline, instant, and every row carries the handbook page it came from. A miss
     * is an honest refusal rather than a guess.
     */
    private suspend fun specLookup(call: ToolCall): ToolAnswer {
        val query = call.args["query"].orEmpty().lowercase().trim()
        val facts = db.factDao().allOnce()
        val hit = facts.firstOrNull { it.key.lowercase().contains(query) }
            ?: facts.filter { fact ->
                query.split(Regex("\\s+")).filter { it.length > 2 }.any {
                    fact.label.lowercase().contains(it) || fact.key.lowercase().contains(it)
                }
            }.maxByOrNull { fact ->
                query.split(Regex("\\s+")).count { fact.label.lowercase().contains(it) }
            }

        if (hit == null) {
            return ToolAnswer(
                SafetyTopics.REFUSAL,
                Provenance.GENERAL,
                sources = emptyList(),
                isSafetyCritical = true,
            )
        }
        val value = if (hit.unit.isNullOrBlank()) hit.value else "${hit.value} ${hit.unit}"
        return ToolAnswer(
            text = "${hit.label}: $value" + (hit.notes?.let { "\n$it" } ?: ""),
            provenance = Provenance.fromSource(hit.source),
            pageRef = hit.pageRef,
            sources = listOf(value, hit.notes.orEmpty(), hit.pageRef?.toString().orEmpty()),
            isSafetyCritical = hit.isSafetyCritical,
        )
    }

    private fun parseDay(text: String): Long? =
        runCatching { LocalDate.parse(text.trim()).toEpochDay() }.getOrNull()

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
