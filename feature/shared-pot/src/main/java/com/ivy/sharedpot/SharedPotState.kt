package com.ivy.sharedpot

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.util.UUID

@Immutable
data class SharedPotState(
    val loading: Boolean = true,
    val setUp: Boolean = false,
    val potName: String = "",
    val currency: String = "",
    val limit: Double = 0.0,
    val spent: Double = 0.0,
    /** Money into the pot this month - a top-up, not a spend. Shown so the pot's own maths adds up. */
    val added: Double = 0.0,
    val daysLeft: Int = 0,
    val dayOfMonth: Int = 0,
    val daysInMonth: Int = 0,
    val recent: ImmutableList<PotEntry> = persistentListOf(),
    val accountOptions: ImmutableList<AccountOption> = persistentListOf(),
    val pickingAccount: Boolean = false,
    val editingLimit: Boolean = false,
) {
    val left: Double get() = limit - spent

    /**
     * What is safe to spend today if the rest of the month is to stay inside the limit.
     *
     * The number the pot exists to produce. A month total tells you how you did afterwards;
     * this tells you what to do at the counter, which is when the decision is actually made.
     */
    val safeDailySpend: Double
        get() = if (daysLeft <= 0) 0.0 else (left / daysLeft).coerceAtLeast(0.0)

    /** Where spending should be by today if it were spread evenly. */
    val onPaceSpend: Double
        get() = if (daysInMonth <= 0) 0.0 else limit * (dayOfMonth.toDouble() / daysInMonth)

    /** Positive means ahead of an even pace, i.e. spending faster than the month allows. */
    val paceDelta: Double get() = spent - onPaceSpend

    val overspent: Boolean get() = left < 0
}

@Immutable
data class AccountOption(
    val id: UUID,
    val name: String,
    val currency: String,
)

@Immutable
data class PotEntry(
    val id: UUID,
    val title: String,
    val amount: Double,
    val income: Boolean,
    val timeLabel: String,
)
