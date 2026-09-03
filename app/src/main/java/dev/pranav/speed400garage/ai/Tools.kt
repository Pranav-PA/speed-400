package dev.pranav.speed400garage.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The tool surface the router picks from (§10.1).
 *
 * This schema — names and parameter types only — is the ONLY thing besides the
 * question text that crosses the network. No values, no rows, no history. That is the
 * whole point of §10.6: the model plans, the device computes.
 */
object Tools {

    const val SUM_EXPENSES = "sum_expenses"
    const val LAST_EVENT = "last_event"
    const val KM_SINCE = "km_since"
    const val FUEL_ECONOMY = "fuel_economy"
    const val CURRENT_ODOMETER = "current_odometer"
    const val DUE_ITEMS = "due_items"
    const val SERVICE_HISTORY = "service_history"
    const val FIND_DOCUMENTS = "find_documents"
    const val SEARCH_NOTES = "search_notes"
    const val SPEC_LOOKUP = "spec_lookup"

    /** Tools whose results are personal. Never sent back to the model (§10.6). */
    val RECORD_TOOLS = setOf(
        SUM_EXPENSES, LAST_EVENT, KM_SINCE, FUEL_ECONOMY, CURRENT_ODOMETER,
        DUE_ITEMS, SERVICE_HISTORY, FIND_DOCUMENTS, SEARCH_NOTES,
    )

    /** Handbook lookups contain no personal data, so they are safe to round-trip. */
    val KNOWLEDGE_TOOLS = setOf(SPEC_LOOKUP)

    /**
     * The function-calling declarations, as Gemini expects them.
     *
     * Descriptions matter more than usual here: the model never sees the data, so the
     * description is the only thing telling it which tool answers which question.
     */
    fun declarations(): List<FunctionDeclaration> = listOf(
        FunctionDeclaration(
            name = SUM_EXPENSES,
            description = "Total money spent, optionally filtered by category and date range. " +
                "Categories: fuel, labour, parts, consumables, accessories, insurance, puc, rto, " +
                "washing, parking, tolls, gear, other.",
            parameters = Schema.obj(
                "category" to Schema.string("Spend category, or omit for everything."),
                "from" to Schema.string("Inclusive start date, YYYY-MM-DD. Omit for all time."),
                "to" to Schema.string("Inclusive end date, YYYY-MM-DD. Omit for today."),
            ),
        ),
        FunctionDeclaration(
            name = LAST_EVENT,
            description = "When something was last done — e.g. engine_oil, chain_lube, brake_pads_front. " +
                "Use the component key when the question is about maintenance.",
            parameters = Schema.obj(
                "component_key" to Schema.string("Component key, e.g. engine_oil."),
                "event_type" to Schema.string("Or an event type: fuel, service, repair, part, document, fault."),
            ),
        ),
        FunctionDeclaration(
            name = KM_SINCE,
            description = "Kilometres ridden since a component was last serviced or replaced.",
            parameters = Schema.obj("component_key" to Schema.string("Component key, e.g. chain_lube.")),
        ),
        FunctionDeclaration(
            name = FUEL_ECONOMY,
            description = "Fuel economy in km/l — the rolling figure over recent full tanks, and the latest tank.",
            parameters = Schema.obj(),
        ),
        FunctionDeclaration(
            name = CURRENT_ODOMETER,
            description = "Today's odometer, projected from recent readings, with how fresh that estimate is.",
            parameters = Schema.obj(),
        ),
        FunctionDeclaration(
            name = DUE_ITEMS,
            description = "What maintenance or documents are due or overdue.",
            parameters = Schema.obj(),
        ),
        FunctionDeclaration(
            name = SERVICE_HISTORY,
            description = "Past services and repairs, newest first.",
            parameters = Schema.obj("limit" to Schema.integer("How many to return. Default 10.")),
        ),
        FunctionDeclaration(
            name = FIND_DOCUMENTS,
            description = "Insurance, PUC, RC and other documents with their expiry dates.",
            parameters = Schema.obj("doc_type" to Schema.string("insurance, puc, rc, warranty… or omit for all.")),
        ),
        FunctionDeclaration(
            name = SEARCH_NOTES,
            description = "Free-text search across every logged entry and the owner's own notes.",
            parameters = Schema.obj("text" to Schema.string("What to search for.")),
        ),
        FunctionDeclaration(
            name = SPEC_LOOKUP,
            description = "A specification from the owner's handbook — tyre pressures, oil grade and " +
                "capacity, coolant, spark plug, chain slack, torque figures, service intervals. " +
                "Every result carries the handbook page it came from.",
            parameters = Schema.obj("query" to Schema.string("What specification is wanted.")),
        ),
    )
}

@Serializable
data class FunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: Schema,
)

@Serializable
data class Schema(
    val type: String,
    val description: String? = null,
    val properties: Map<String, Schema>? = null,
    val required: List<String>? = null,
) {
    companion object {
        fun string(description: String) = Schema("STRING", description)
        fun integer(description: String) = Schema("INTEGER", description)
        fun obj(vararg props: Pair<String, Schema>) =
            Schema("OBJECT", properties = if (props.isEmpty()) emptyMap() else props.toMap())
    }
}

/** What the router decided. */
@Serializable
data class ToolCall(
    val name: String,
    @SerialName("args") val args: Map<String, String> = emptyMap(),
)
