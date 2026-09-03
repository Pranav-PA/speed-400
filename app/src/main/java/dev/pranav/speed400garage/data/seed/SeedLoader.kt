package dev.pranav.speed400garage.data.seed

import android.content.Context
import dev.pranav.speed400garage.data.db.GarageDatabase
import dev.pranav.speed400garage.data.db.entity.BikeEntity
import dev.pranav.speed400garage.data.db.entity.ComponentEntity
import dev.pranav.speed400garage.data.db.entity.FactEntity
import dev.pranav.speed400garage.data.db.entity.SettingEntity
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds the component catalogue and the fact table from the bundled handbook extract.
 *
 * Upsert rather than insert-if-empty, so shipping a corrected `components.json` in a
 * later build actually corrects the device. Rows are keyed on the natural key, and a
 * fact's [FactEntity.verifiedOn] is deliberately preserved across a reseed — Pranav's
 * verification is his, and a rebuild must not silently throw it away.
 */
@Singleton
class SeedLoader @Inject constructor(
    private val context: Context,
    private val db: GarageDatabase,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun seedIfNeeded() {
        seedComponents()
        seedFacts()
        seedBikeIfAbsent()
    }

    private suspend fun seedComponents() {
        val file = json.decodeFromString<ComponentSeedFile>(readAsset(COMPONENTS_ASSET))
        val dao = db.componentDao()
        val existing = dao.count()
        val rows = file.components.map { seed ->
            val prior = dao.byKey(seed.key)
            ComponentEntity(
                id = prior?.id ?: dev.pranav.speed400garage.data.db.entity.newId(),
                key = seed.key,
                displayName = seed.displayName,
                actionKind = seed.actionKind,
                intervalKm = seed.intervalKm,
                intervalDays = seed.intervalDays,
                intervalSource = seed.intervalSource,
                manualPageRef = seed.manualPageRef,
                isWarrantyRelevant = seed.isWarrantyRelevant,
                isDailyCheck = seed.isDailyCheck,
                isOneOff = seed.oneOff,
                firstDueKm = seed.firstDueKm,
                notes = seed.notes,
                createdAt = prior?.createdAt ?: dev.pranav.speed400garage.data.db.entity.now(),
            )
        }
        dao.upsertAll(rows)
        db.settingDao().put(
            SettingEntity(
                key = KEY_HANDBOOK_PART_NUMBER,
                value = file.sourceDocument.partNumber,
            )
        )
        if (existing == 0) {
            db.settingDao().put(SettingEntity(key = KEY_SEEDED_COMPONENTS_AT, value = dev.pranav.speed400garage.data.db.entity.now().toString()))
        }
    }

    private suspend fun seedFacts() {
        val file = json.decodeFromString<FactSeedFile>(readAsset(FACTS_ASSET))
        val dao = db.factDao()
        val rows = file.facts.map { seed ->
            val prior = dao.byKey(seed.key)
            FactEntity(
                id = prior?.id ?: dev.pranav.speed400garage.data.db.entity.newId(),
                key = seed.key,
                label = seed.label,
                value = seed.value,
                unit = seed.unit,
                category = seed.category,
                source = seed.source,
                pageRef = seed.pageRef,
                isSafetyCritical = seed.isSafetyCritical,
                notes = seed.notes,
                // Preserve an owner verification that already happened on this device.
                verifiedOn = prior?.verifiedOn,
                createdAt = prior?.createdAt ?: dev.pranav.speed400garage.data.db.entity.now(),
            )
        }
        dao.upsertAll(rows)
    }

    /**
     * Phase 0's "the app opens and knows the bike exists". Registration, VIN, purchase
     * date and price are Pranav's to fill in during backfill onboarding (§5.10) — the
     * app must not invent them, so they stay null.
     */
    private suspend fun seedBikeIfAbsent() {
        val dao = db.bikeDao()
        if (dao.count() > 0) return
        dao.upsert(
            BikeEntity(
                make = "Triumph",
                model = "Speed 400",
                modelYear = 2024,
                registration = null,
                vin = null,
                engineNumber = null,
                colour = null,
                purchasedOn = null,
                purchasePricePaise = null,
                photoUri = null,
                handbookPartNumber = db.settingDao().get(KEY_HANDBOOK_PART_NUMBER),
            )
        )
    }

    private fun readAsset(name: String): String =
        context.assets.open(name).bufferedReader().use { it.readText() }

    companion object {
        const val COMPONENTS_ASSET = "seed/components.json"
        const val FACTS_ASSET = "seed/facts.json"
        const val KEY_HANDBOOK_PART_NUMBER = "handbook.part_number"
        const val KEY_SEEDED_COMPONENTS_AT = "seed.components_at"
    }
}
