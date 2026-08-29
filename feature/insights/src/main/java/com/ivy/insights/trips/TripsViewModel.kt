package com.ivy.insights.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.base.time.TimeProvider
import com.ivy.domain.usecase.quickadd.QuickAddOptionsUseCase
import com.ivy.domain.usecase.trip.Trip
import com.ivy.domain.usecase.trip.TripStore
import com.ivy.domain.usecase.trip.TripSummaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TripsViewModel @Inject constructor(
    private val tripStore: TripStore,
    private val tripSummaryUseCase: TripSummaryUseCase,
    private val optionsUseCase: QuickAddOptionsUseCase,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(TripsState())
    val state: StateFlow<TripsState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onEvent(event: TripsEvent) {
        when (event) {
            TripsEvent.AddTrip -> _state.update {
                val today = timeProvider.localDateNow()
                it.copy(draft = TripDraft(startDate = today, endDate = today))
            }

            is TripsEvent.EditTrip -> editTrip(event.id)
            is TripsEvent.DeleteTrip -> viewModelScope.launch {
                tripStore.delete(event.id)
                refreshNow()
            }

            is TripsEvent.ToggleExpanded -> _state.update {
                it.copy(expandedTripId = if (it.expandedTripId == event.id) null else event.id)
            }

            is TripsEvent.UpdateDraft -> _state.update { it.copy(draft = event.draft) }
            TripsEvent.SaveDraft -> saveDraft()
            TripsEvent.DismissDraft -> _state.update { it.copy(draft = null) }
        }
    }

    private fun editTrip(id: UUID) {
        viewModelScope.launch {
            val trip = tripStore.findById(id) ?: return@launch
            _state.update {
                it.copy(
                    draft = TripDraft(
                        id = trip.id,
                        name = trip.name,
                        startDate = trip.startDate,
                        endDate = trip.endDateInclusive,
                        accountIds = trip.accountIds,
                        currency = trip.currency.orEmpty(),
                    )
                )
            }
        }
    }

    private fun saveDraft() {
        val draft = _state.value.draft ?: return
        if (!draft.isValid) return

        viewModelScope.launch {
            tripStore.save(
                Trip(
                    id = draft.id ?: UUID.randomUUID(),
                    name = draft.name.trim(),
                    startDate = draft.startDate,
                    endDateInclusive = draft.endDate,
                    accountIds = draft.accountIds,
                    currency = draft.currency.trim().takeIf { it.isNotBlank() }?.uppercase(),
                )
            )
            _state.update { it.copy(draft = null) }
            refreshNow()
        }
    }

    private fun refresh() {
        viewModelScope.launch { refreshNow() }
    }

    private suspend fun refreshNow() {
        val summaries = runCatching { tripSummaryUseCase.summarizeAll() }.getOrDefault(emptyList())
        val options = runCatching { optionsUseCase.load() }.getOrNull()

        _state.update {
            it.copy(
                loading = false,
                trips = summaries.toImmutableList(),
                accounts = options?.accounts.orEmpty().toImmutableList(),
            )
        }
    }
}
