package dev.pranav.speed400garage.ui.chart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pranav.speed400garage.data.db.dao.BikeDao
import dev.pranav.speed400garage.data.db.dao.CostDao
import dev.pranav.speed400garage.data.db.dao.FuelDao
import dev.pranav.speed400garage.data.db.dao.OdometerDao
import dev.pranav.speed400garage.data.repo.GarageRepository
import dev.pranav.speed400garage.domain.engine.Analytics
import dev.pranav.speed400garage.domain.engine.CategoryTotal
import dev.pranav.speed400garage.domain.engine.CostEngine
import dev.pranav.speed400garage.domain.engine.CostLine
import dev.pranav.speed400garage.domain.engine.CostPerKm
import dev.pranav.speed400garage.domain.engine.Fill
import dev.pranav.speed400garage.domain.engine.FillType
import dev.pranav.speed400garage.domain.engine.FuelEconomyCalculator
import dev.pranav.speed400garage.domain.engine.MonthBucket
import dev.pranav.speed400garage.domain.engine.Reading
import dev.pranav.speed400garage.domain.engine.SpendRow
import dev.pranav.speed400garage.domain.engine.TankSpan
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AnalyticsState(
    val kmPerMonth: List<MonthBucket> = emptyList(),
    val spendPerMonth: List<MonthBucket> = emptyList(),
    val categories: List<CategoryTotal> = emptyList(),
    val tankKmpl: List<Double> = emptyList(),
    val rollingKmpl: List<Double> = emptyList(),
    val spans: List<TankSpan> = emptyList(),
    val cost: CostPerKm? = null,
    val lifetimePaise: Long = 0,
    val usableRangeKm: Int? = null,
    val fullTankRangeKm: Int? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repo: GarageRepository,
    bikeDao: BikeDao,
    fuelDao: FuelDao,
    costDao: CostDao,
    odometerDao: OdometerDao,
) : ViewModel() {

    private val bike = bikeDao.observeActiveBike()

    private val readings = bike.flatMapLatest { b ->
        if (b == null) flowOf(emptyList()) else odometerDao.observeReadings(b.id)
    }.map { list -> list.map { Reading(repo.epochDayOf(it.readAt), it.odometerKm) } }

    private val fills = bike.flatMapLatest { b ->
        if (b == null) flowOf(emptyList()) else fuelDao.observeFills(b.id)
    }

    private val costs = bike.flatMapLatest { b ->
        if (b == null) flowOf(emptyList()) else costDao.observeAll(b.id)
    }

    val state: StateFlow<AnalyticsState> = combine(bike, readings, fills, costs) { bike, readings, fills, costs ->
        val today = repo.today()

        val spans = FuelEconomyCalculator.spans(
            fills.map {
                Fill(
                    id = it.eventId,
                    epochDay = repo.epochDayOf(it.occurredAt),
                    odometerKm = it.odometerKm,
                    litres = it.litres,
                    fillType = runCatching { FillType.valueOf(it.fillType.uppercase()) }.getOrDefault(FillType.FULL),
                    missedPrevious = it.missedPrevious,
                    amountPaise = it.amountPaise,
                )
            }
        )
        val rolling = FuelEconomyCalculator.rollingKmpl(spans)

        val spendRows = costs.map { SpendRow(it.category, it.amountPaise, repo.epochDayOf(it.occurredAt)) }
        val lines = costs.map { CostLine(it.category, it.amountPaise) }

        val observedSpan = if (readings.size >= 2) readings.maxOf { it.km } - readings.minOf { it.km } else 0
        val yearAgo = today - 365
        val yearLines = costs.filter { repo.epochDayOf(it.occurredAt) >= yearAgo }
            .map { CostLine(it.category, it.amountPaise) }
        val yearSpan = readings.filter { it.epochDay >= yearAgo }
            .let { if (it.size >= 2) it.maxOf { r -> r.km } - it.minOf { r -> r.km } else 0 }

        AnalyticsState(
            kmPerMonth = Analytics.kmPerMonth(readings, months = 12, today = today),
            spendPerMonth = Analytics.spendPerMonth(spendRows, months = 12, today = today),
            categories = Analytics.byCategory(spendRows),
            tankKmpl = spans.map { it.kmpl },
            rollingKmpl = Analytics.rollingSeries(spans),
            spans = spans,
            cost = CostEngine.costPerKm(
                windowLines = yearLines,
                windowDistanceKm = yearSpan,
                allLines = lines,
                totalDistanceSincePurchaseKm = observedSpan,
                depreciationPaise = CostEngine.depreciation(bike?.purchasePricePaise, null),
            ),
            lifetimePaise = costs.sumOf { it.amountPaise },
            usableRangeKm = Analytics.usableRangeKm(rolling),
            fullTankRangeKm = Analytics.fullTankRangeKm(rolling),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsState())
}
