package com.ivy.sharedpot

import java.util.UUID

sealed interface SharedPotEvent {
    data object OpenAccountPicker : SharedPotEvent
    data object DismissAccountPicker : SharedPotEvent
    data class PickAccount(val accountId: UUID) : SharedPotEvent

    data object EditLimit : SharedPotEvent
    data object DismissLimit : SharedPotEvent
    data class SetLimit(val limit: Double) : SharedPotEvent

    data object Refresh : SharedPotEvent
}
