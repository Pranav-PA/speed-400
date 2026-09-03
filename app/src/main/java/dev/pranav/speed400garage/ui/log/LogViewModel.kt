package dev.pranav.speed400garage.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pranav.speed400garage.data.repo.GarageRepository
import dev.pranav.speed400garage.domain.engine.EntryValidator
import dev.pranav.speed400garage.domain.engine.Fill
import dev.pranav.speed400garage.domain.engine.FillType
import dev.pranav.speed400garage.domain.engine.FuelEconomyCalculator
import dev.pranav.speed400garage.domain.engine.ValidationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the entry sheet is doing right now. */
sealed interface LogState {
    data object Editing : LogState
    /** Questions §9.2 raised. The entry is still savable unless [result] blocks. */
    data class Asking(val result: ValidationResult) : LogState
    data object Saved : LogState
    data class Error(val message: String) : LogState
}

@HiltViewModel
class LogViewModel @Inject constructor(
    private val repo: GarageRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<LogState>(LogState.Editing)
    val state: StateFlow<LogState> = _state.asStateFlow()

    private val _lastOdometer = MutableStateFlow<Int?>(null)
    val lastOdometer: StateFlow<Int?> = _lastOdometer.asStateFlow()

    private val _lastRatePaise = MutableStateFlow<Long?>(null)
    val lastRatePaise: StateFlow<Long?> = _lastRatePaise.asStateFlow()

    init { refreshContext() }

    fun refreshContext() = viewModelScope.launch {
        val bikeId = repo.activeBikeId() ?: return@launch
        _lastOdometer.value = repo.lastOdometerKm(bikeId)
        _lastRatePaise.value = repo.lastPricePerLitrePaise(bikeId)
    }

    fun reset() { _state.value = LogState.Editing }

    /**
     * Validates, then saves unless something blocks.
     *
     * [confirmed] is set once the rider has seen the questions and chosen to go ahead.
     * The questions are never a gate on the data itself — only a backwards odometer
     * actually stops a save (§9.2).
     */
    fun saveFuel(
        odometerKm: Int?,
        amountPaise: Long,
        pricePerLitrePaise: Long?,
        litres: Double,
        fillType: FillType,
        missedPrevious: Boolean,
        isComputedLitres: Boolean,
        station: String?,
        notes: String?,
        occurredAtMillis: Long,
        confirmed: Boolean = false,
    ) = viewModelScope.launch {
        val bikeId = repo.activeBikeId() ?: run {
            _state.value = LogState.Error("No bike on file yet.")
            return@launch
        }

        // Work out what this fill would imply, so §9.2 can ask about a bad tank at the
        // moment it is entered rather than six months later in a chart.
        val existing = repo.fills(bikeId)
        val report = FuelEconomyCalculator.report(existing)
        val hypothetical = existing + Fill(
            id = "pending",
            epochDay = repo.epochDayOf(occurredAtMillis),
            odometerKm = odometerKm,
            litres = litres,
            fillType = fillType,
            missedPrevious = missedPrevious,
        )
        val newSpan = FuelEconomyCalculator.spans(hypothetical)
            .lastOrNull { it.toFillId == "pending" }

        val validation = EntryValidator.validate(
            odometerKm = odometerKm,
            lastKnownOdometerKm = _lastOdometer.value,
            litres = litres,
            pricePerLitrePaise = pricePerLitrePaise,
            lastPricePerLitrePaise = _lastRatePaise.value,
            computedKmpl = newSpan?.kmpl,
            rollingKmpl = report.rollingKmpl,
        )

        if (!validation.canSave) { _state.value = LogState.Asking(validation); return@launch }
        if (validation.questions.isNotEmpty() && !confirmed) {
            _state.value = LogState.Asking(validation); return@launch
        }

        runCatching {
            repo.logFuel(
                bikeId, occurredAtMillis, odometerKm, amountPaise, pricePerLitrePaise,
                litres, fillType, missedPrevious, isComputedLitres, station, notes,
            )
        }.onSuccess { _state.value = LogState.Saved; refreshContext() }
            .onFailure { _state.value = LogState.Error(it.message ?: "Could not save.") }
    }

    fun saveExpense(
        occurredAtMillis: Long,
        odometerKm: Int?,
        title: String,
        category: String,
        amountPaise: Long,
        notes: String?,
        confirmed: Boolean = false,
    ) = viewModelScope.launch {
        val bikeId = repo.activeBikeId() ?: return@launch
        val validation = EntryValidator.validate(odometerKm, _lastOdometer.value)
        if (!validation.canSave) { _state.value = LogState.Asking(validation); return@launch }
        if (validation.questions.isNotEmpty() && !confirmed) {
            _state.value = LogState.Asking(validation); return@launch
        }
        runCatching { repo.logExpense(bikeId, occurredAtMillis, odometerKm, title, category, amountPaise, notes) }
            .onSuccess { _state.value = LogState.Saved; refreshContext() }
            .onFailure { _state.value = LogState.Error(it.message ?: "Could not save.") }
    }

    fun saveService(
        occurredAtMillis: Long,
        odometerKm: Int?,
        title: String,
        vendorName: String?,
        lines: List<Pair<String, Long>>,
        componentKeys: List<String>,
        action: String,
        notes: String?,
        confirmed: Boolean = false,
    ) = viewModelScope.launch {
        val bikeId = repo.activeBikeId() ?: return@launch
        val validation = EntryValidator.validate(odometerKm, _lastOdometer.value)
        if (!validation.canSave) { _state.value = LogState.Asking(validation); return@launch }
        if (validation.questions.isNotEmpty() && !confirmed) {
            _state.value = LogState.Asking(validation); return@launch
        }
        runCatching {
            repo.logService(bikeId, occurredAtMillis, odometerKm, title, vendorName, lines, componentKeys, action, notes)
        }.onSuccess { _state.value = LogState.Saved; refreshContext() }
            .onFailure { _state.value = LogState.Error(it.message ?: "Could not save.") }
    }

    fun saveDocument(
        occurredAtMillis: Long,
        docType: String,
        issuer: String?,
        number: String?,
        expiresOnMillis: Long?,
        secondaryExpiresOnMillis: Long?,
        amountPaise: Long?,
        notes: String?,
    ) = viewModelScope.launch {
        val bikeId = repo.activeBikeId() ?: return@launch
        runCatching {
            repo.logDocument(
                bikeId, occurredAtMillis, docType, issuer, number,
                issuedOnMillis = null, expiresOnMillis = expiresOnMillis,
                secondaryExpiresOnMillis = secondaryExpiresOnMillis,
                amountPaise = amountPaise, notes = notes,
            )
        }.onSuccess { _state.value = LogState.Saved }
            .onFailure { _state.value = LogState.Error(it.message ?: "Could not save.") }
    }

    fun saveFault(
        occurredAtMillis: Long,
        odometerKm: Int?,
        summary: String,
        notes: String?,
    ) = viewModelScope.launch {
        val bikeId = repo.activeBikeId() ?: return@launch
        runCatching { repo.logFault(bikeId, occurredAtMillis, odometerKm, summary, notes) }
            .onSuccess { _state.value = LogState.Saved; refreshContext() }
            .onFailure { _state.value = LogState.Error(it.message ?: "Could not save.") }
    }

    fun saveOdometer(occurredAtMillis: Long, odometerKm: Int, confirmed: Boolean = false) = viewModelScope.launch {
        val bikeId = repo.activeBikeId() ?: return@launch
        val validation = EntryValidator.validate(odometerKm, _lastOdometer.value)
        if (!validation.canSave) { _state.value = LogState.Asking(validation); return@launch }
        if (validation.questions.isNotEmpty() && !confirmed) {
            _state.value = LogState.Asking(validation); return@launch
        }
        runCatching { repo.logOdometer(bikeId, occurredAtMillis, odometerKm) }
            .onSuccess { _state.value = LogState.Saved; refreshContext() }
            .onFailure { _state.value = LogState.Error(it.message ?: "Could not save.") }
    }
}
