package dev.pranav.speed400garage.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pranav.speed400garage.data.db.dao.BikeDao
import dev.pranav.speed400garage.data.db.dao.CaptureInboxDao
import dev.pranav.speed400garage.data.db.dao.ComponentDao
import dev.pranav.speed400garage.data.db.dao.EventDao
import dev.pranav.speed400garage.data.db.dao.FactDao
import dev.pranav.speed400garage.data.db.entity.ComponentEntity
import dev.pranav.speed400garage.data.db.entity.FactEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class GarageUiState(
    val bikeName: String? = null,
    val registration: String? = null,
    val handbookPartNumber: String? = null,
    val componentCount: Int = 0,
    val componentsCitedToManual: Int = 0,
    val factCount: Int = 0,
    val factsVerifiedByOwner: Int = 0,
    val eventCount: Int = 0,
    val inboxPending: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GarageViewModel @Inject constructor(
    bikeDao: BikeDao,
    eventDao: EventDao,
    componentDao: ComponentDao,
    factDao: FactDao,
    captureInboxDao: CaptureInboxDao,
) : ViewModel() {

    val components: StateFlow<List<ComponentEntity>> =
        componentDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val facts: StateFlow<List<FactEntity>> =
        factDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val bike = bikeDao.observeActiveBike()

    private val eventCount = bike.flatMapLatest { b ->
        if (b == null) flowOf(0) else eventDao.observeCount(b.id)
    }

    val state: StateFlow<GarageUiState> = combine(
        bike,
        components,
        facts,
        factDao.observeVerifiedCount(),
        combine(eventCount, captureInboxDao.observePendingCount()) { events, inbox -> events to inbox },
    ) { bike, components, facts, verified, counts ->
        GarageUiState(
            bikeName = bike?.let { "${it.make} ${it.model}" },
            registration = bike?.registration,
            handbookPartNumber = bike?.handbookPartNumber,
            componentCount = components.size,
            componentsCitedToManual = components.count { it.intervalSource == "manual" && it.manualPageRef != null },
            factCount = facts.size,
            factsVerifiedByOwner = verified,
            eventCount = counts.first,
            inboxPending = counts.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GarageUiState())
}
