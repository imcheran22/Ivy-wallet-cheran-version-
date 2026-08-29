package com.ivy.insights.trips

import androidx.compose.runtime.Immutable
import com.ivy.domain.usecase.quickadd.QuickAddAccountOption
import com.ivy.domain.usecase.trip.TripSummary
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.time.LocalDate
import java.util.UUID

@Immutable
data class TripDraft(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val id: UUID? = null,
    val name: String = "",
    val accountIds: List<UUID> = emptyList(),
    val currency: String = "",
) {
    val isValid: Boolean
        get() = name.isNotBlank() && !endDate.isBefore(startDate)
}

@Immutable
data class TripsState(
    val loading: Boolean = true,
    val trips: ImmutableList<TripSummary> = persistentListOf(),
    val accounts: ImmutableList<QuickAddAccountOption> = persistentListOf(),
    val expandedTripId: UUID? = null,
    val draft: TripDraft? = null,
)

sealed interface TripsEvent {
    data object AddTrip : TripsEvent
    data class EditTrip(val id: UUID) : TripsEvent
    data class DeleteTrip(val id: UUID) : TripsEvent
    data class ToggleExpanded(val id: UUID) : TripsEvent
    data class UpdateDraft(val draft: TripDraft) : TripsEvent
    data object SaveDraft : TripsEvent
    data object DismissDraft : TripsEvent
}
