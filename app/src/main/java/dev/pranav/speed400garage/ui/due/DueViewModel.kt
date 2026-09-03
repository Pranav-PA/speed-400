package dev.pranav.speed400garage.ui.due

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pranav.speed400garage.data.db.dao.BikeDao
import dev.pranav.speed400garage.data.db.dao.ComponentDao
import dev.pranav.speed400garage.data.db.dao.DocumentDao
import dev.pranav.speed400garage.data.db.dao.FaultDao
import dev.pranav.speed400garage.data.db.dao.OdometerDao
import dev.pranav.speed400garage.data.db.entity.FaultEntity
import dev.pranav.speed400garage.data.repo.GarageRepository
import dev.pranav.speed400garage.domain.engine.Baseline
import dev.pranav.speed400garage.domain.engine.DueComponent
import dev.pranav.speed400garage.domain.engine.DueDocument
import dev.pranav.speed400garage.domain.engine.DueEngine
import dev.pranav.speed400garage.domain.engine.DueItem
import dev.pranav.speed400garage.domain.engine.OdometerProjector
import dev.pranav.speed400garage.domain.engine.Reading
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DueState(
    val items: List<DueItem> = emptyList(),
    val stalenessNudge: String? = null,
    val openFaults: List<FaultEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DueViewModel @Inject constructor(
    private val repo: GarageRepository,
    bikeDao: BikeDao,
    componentDao: ComponentDao,
    documentDao: DocumentDao,
    odometerDao: OdometerDao,
    faultDao: FaultDao,
) : ViewModel() {

    private val bike = bikeDao.observeActiveBike()

    /** Recomputed on every write, per §8.2 — not only on the daily WorkManager pass. */
    private val baselines = MutableStateFlow<Pair<Map<String, Baseline>, Baseline?>>(emptyMap<String, Baseline>() to null)

    private val readings = bike.flatMapLatest { b ->
        if (b == null) flowOf(emptyList()) else odometerDao.observeReadings(b.id)
    }.map { list -> list.map { Reading(repo.epochDayOf(it.readAt), it.odometerKm) } }

    private val faults = bike.flatMapLatest { b ->
        if (b == null) flowOf(emptyList()) else faultDao.observeOpen(b.id)
    }

    val state: StateFlow<DueState> = combine(
        componentDao.observeAll(),
        documentDao.observeAll(),
        readings,
        baselines,
        faults,
    ) { components, documents, readings, (byKey, start), faults ->
        val today = repo.today()
        val projection = OdometerProjector.project(readings, today)

        DueState(
            items = DueEngine.compute(
                components = components.map {
                    DueComponent(
                        key = it.key, label = it.displayName,
                        intervalKm = it.intervalKm, intervalDays = it.intervalDays,
                        intervalSource = it.intervalSource, manualPageRef = it.manualPageRef,
                        isWarrantyRelevant = it.isWarrantyRelevant, isDailyCheck = it.isDailyCheck,
                        isOneOff = it.isOneOff, firstDueKm = it.firstDueKm,
                    )
                },
                baselines = byKey,
                bikeStart = start,
                documents = documents.map {
                    DueDocument(
                        id = it.id,
                        label = it.docType.replaceFirstChar { c -> c.uppercase() } + (it.issuer?.let { i -> " — $i" } ?: ""),
                        expiresOnDay = it.expiresOn?.let { e -> repo.epochDayOf(e) },
                        secondaryExpiresOnDay = it.secondaryExpiresOn?.let { e -> repo.epochDayOf(e) },
                        secondaryLabel = if (it.docType == "insurance") "Insurance — third-party cover" else null,
                    )
                },
                projection = projection,
                today = today,
            ),
            stalenessNudge = DueEngine.stalenessNudge(projection, today),
            openFaults = faults,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DueState())

    init { refreshBaselines() }

    fun refreshBaselines() = viewModelScope.launch {
        val bikeId = repo.activeBikeId() ?: return@launch
        baselines.value = repo.baselines(bikeId) to repo.bikeStart(bikeId)
    }

    fun closeFault(id: String) = viewModelScope.launch {
        repo.setFaultStatus(id, "resolved")
    }
}
