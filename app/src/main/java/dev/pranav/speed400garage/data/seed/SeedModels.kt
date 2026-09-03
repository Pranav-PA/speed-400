package dev.pranav.speed400garage.data.seed

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The wire shape of `assets/seed/components.json` and `assets/seed/facts.json`, both
 * produced in Phase 0 by reading the owner's handbook (part 3850838-IN) page by page.
 *
 * These files are the app's ground truth. Nothing in them was filled in from general
 * knowledge: anything the handbook does not state carries `interval_source: unverified`
 * and a note saying so (§3 P5).
 */
@Serializable
data class SeedSourceDocument(
    val title: String,
    @SerialName("part_number") val partNumber: String? = null,
    val market: String? = null,
    @SerialName("pdf_created") val pdfCreated: String? = null,
    val pages: Int? = null,
    @SerialName("retrieved_from") val retrievedFrom: String? = null,
)

@Serializable
data class ComponentSeedFile(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("source_document") val sourceDocument: SeedSourceDocument,
    val components: List<ComponentSeed>,
)

@Serializable
data class ComponentSeed(
    val key: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("action_kind") val actionKind: String,
    @SerialName("interval_km") val intervalKm: Int? = null,
    @SerialName("interval_days") val intervalDays: Int? = null,
    @SerialName("interval_source") val intervalSource: String,
    @SerialName("manual_page_ref") val manualPageRef: Int? = null,
    @SerialName("is_warranty_relevant") val isWarrantyRelevant: Boolean = false,
    @SerialName("is_daily_check") val isDailyCheck: Boolean = false,
    @SerialName("one_off") val oneOff: Boolean = false,
    @SerialName("first_due_km") val firstDueKm: Int? = null,
    val notes: String? = null,
)

@Serializable
data class FactSeedFile(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("source_document") val sourceDocument: SeedSourceDocument,
    val facts: List<FactSeed>,
)

@Serializable
data class FactSeed(
    val key: String,
    val label: String,
    val value: String,
    val unit: String? = null,
    val category: String,
    val source: String,
    @SerialName("page_ref") val pageRef: Int? = null,
    @SerialName("is_safety_critical") val isSafetyCritical: Boolean = false,
    val notes: String? = null,
    /** Always null in the shipped seed — the owner promotes a row, not the build. */
    @SerialName("verified_on") val verifiedOn: String? = null,
)
