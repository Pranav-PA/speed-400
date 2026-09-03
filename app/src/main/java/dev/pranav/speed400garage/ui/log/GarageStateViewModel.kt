package dev.pranav.speed400garage.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pranav.speed400garage.data.db.dao.BikeDao
import dev.pranav.speed400garage.data.db.dao.CostDao
import dev.pranav.speed400garage.data.db.dao.CostRow
import dev.pranav.speed400garage.data.db.dao.EventDao
import dev.pranav.speed400garage.data.db.dao.FillRow
import dev.pranav.speed400garage.data.db.dao.FuelDao
import dev.pranav.speed400garage.data.db.dao.OdometerDao
import dev.pranav.speed400garage.data.db.entity.EventEntity
import dev.pranav.speed400garage.data.repo.GarageRepository
import dev.pranav.speed400garage.domain.engine.CostEngine
import dev.pranav.speed400garage.domain.engine.CostLine
import dev.pranav.speed400garage.domain.engine.CostPerKm
import dev.pranav.speed400garage.domain.engine.EconomyReport
import dev.pranav.speed400garage.domain.engine.Fill
import dev.pranav.speed400garage.domain.engine.FillType
import dev.pranav.speed400garage.domain.engine.FuelEconomyCalculator
import dev.pranav.speed400garage.domain.engine.OdometerProjector
import dev.pranav.speed400garage.domain.engine.Projection
import dev.pranav.speed400garage.domain.engine.Reading
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class GarageSnapshot(
    val bikeName: String? = null,
    val registration: String? = null,
    val projection: Projection? = null,
    val economy: EconomyReport? = null,
    val cost: CostPerKm? = null,
    val totalSpentPaise: Long = 0,
    val spentThisMonthPaise: Long = 0,
    val events: List<EventEntity> = emptyList(),
    val eventCount: Int = 0,
    val fuelCount: Int = 0,
    val hasAnyData: Boolean = false,
)

/**
 * Everything the dashboard and timeline read, derived live from the event log.
 *
 * Nothing here is cached in a table. The engines run over the rows on every change,
 * which keeps §3 P2 honest: the timeline, the spend total and the economy chart are
 * five views of one log rather than five things that can drift apart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GarageStateViewModel @Inject constructor(
    private val repo: GarageRepository,
    bikeDao: BikeDao,
    eventDao: EventDao,
    fuelDao: FuelDao,
    costDao: CostDao,
    odometerDao: OdometerDao,
) : ViewModel() {

    private val bike = bikeDao.observeActiveBike()

    private val events = bike.flatMapLatest { b ->
        if (b == null) flowOf(emptyList()) else eventDao.observeTimeline(b.id)
    }

    private val fills = bike.flatMapLatest { b ->
        if (b == null) flowOf(emptyList()) else fuelDao.observeFills(b.id)
    }

    private val costs = bike.flatMapLatest { b ->
        if (b == null) flowOf(emptyList()) else costDao.observeAll(b.id)
    }

    private val readings = bike.flatMapLatest { b ->
        if (b == null) flowOf(emptyList()) else odometerDao.observeReadings(b.id)
    }.map { list -> list.map { Reading(repo.epochDayOf(it.readAt), it.odometerKm) } }

    val snapshot: StateFlow<GarageSnapshot> = combine(
        bike, events, fills, costs, readings,
    ) { bike, events, fills, costs, readings ->
        val today = repo.today()
        val projection = OdometerProjector.project(readings, today)

        val economy = FuelEconomyCalculator.report(
            fills.map { it.toFill(repo.epochDayOf(it.occurredAt)) }
        )

        val lines = costs.map { CostLine(it.category, it.amountPaise) }
        val monthStartMillis = repo.millisOf(today - 30)
        val monthLines = costs.filter { it.occurredAt >= monthStartMillis }
            .map { CostLine(it.category, it.amountPaise) }

        // Distance is the odometer span actually observed, not a projection — a
        // cost-per-km built on an estimate would be an estimate, and §9.3's numbers
        // are 🔵 my-records figures.
        val observedSpan = if (readings.size >= 2) {
            readings.maxOf { it.km } - readings.minOf { it.km }
        } else 0
        val monthSpan = readings.filter { it.epochDay >= today - 30 }
            .let { if (it.size >= 2) it.maxOf { r -> r.km } - it.minOf { r -> r.km } else 0 }

        val cost = CostEngine.costPerKm(
            windowLines = monthLines,
            windowDistanceKm = monthSpan,
            allLines = lines,
            totalDistanceSincePurchaseKm = observedSpan,
            depreciationPaise = CostEngine.depreciation(bike?.purchasePricePaise, null),
        )

        GarageSnapshot(
            bikeName = bike?.let { "${it.make} ${it.model}" },
            registration = bike?.registration,
            projection = projection,
            economy = economy,
            cost = cost,
            totalSpentPaise = costs.sumOf { it.amountPaise },
            spentThisMonthPaise = costs.filter { it.occurredAt >= monthStartMillis }.sumOf { it.amountPaise },
            events = events,
            eventCount = events.size,
            fuelCount = fills.size,
            hasAnyData = events.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GarageSnapshot())
}

private fun FillRow.toFill(epochDay: Long) = Fill(
    id = eventId,
    epochDay = epochDay,
    odometerKm = odometerKm,
    litres = litres,
    fillType = runCatching { FillType.valueOf(fillType.uppercase()) }.getOrDefault(FillType.FULL),
    missedPrevious = missedPrevious,
    amountPaise = amountPaise,
)

private fun List<CostRow>.sumOfPaise(): Long = sumOf { it.amountPaise }
