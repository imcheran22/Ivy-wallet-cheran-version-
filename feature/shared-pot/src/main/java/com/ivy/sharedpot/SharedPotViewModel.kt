package com.ivy.sharedpot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ivy.data.model.AccountId
import com.ivy.data.model.Expense
import com.ivy.data.model.Income
import com.ivy.data.model.Transaction
import com.ivy.data.model.Transfer
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.TransactionRepository
import com.ivy.ui.ComposeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

/**
 * The shared pot: one account, one monthly limit, and the daily number that follows from them.
 *
 * Everything here is derived - there is no shared-pot table and no shared-pot transaction type.
 * The pot is an account you already have and the spending is transactions you already record,
 * which is what lets both phones see the same pot through the cloud sync that is already in
 * the app rather than through a second sync channel of its own.
 */
@Stable
@HiltViewModel
class SharedPotViewModel @Inject constructor(
    private val settings: SharedPotSettings,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
) : ComposeViewModel<SharedPotState, SharedPotEvent>() {

    private var state by mutableStateOf(SharedPotState())

    @Composable
    override fun uiState(): SharedPotState {
        LaunchedEffect(Unit) { load() }
        return state
    }

    override fun onEvent(event: SharedPotEvent) {
        when (event) {
            SharedPotEvent.OpenAccountPicker -> state = state.copy(pickingAccount = true)
            SharedPotEvent.DismissAccountPicker -> state = state.copy(pickingAccount = false)
            is SharedPotEvent.PickAccount -> pickAccount(event.accountId)

            SharedPotEvent.EditLimit -> state = state.copy(editingLimit = true)
            SharedPotEvent.DismissLimit -> state = state.copy(editingLimit = false)
            is SharedPotEvent.SetLimit -> setLimit(event.limit)

            SharedPotEvent.ConfirmRemove -> state = state.copy(confirmingRemove = true)
            SharedPotEvent.DismissRemove -> state = state.copy(confirmingRemove = false)
            SharedPotEvent.RemovePot -> removePot()

            SharedPotEvent.Refresh -> viewModelScope.launch { load() }
        }
    }

    private fun pickAccount(accountId: UUID) {
        viewModelScope.launch {
            settings.setAccount(accountId)
            state = state.copy(pickingAccount = false)
            load()
        }
    }

    private fun setLimit(limit: Double) {
        state = state.copy(editingLimit = false)
        if (limit <= 0.0) return
        viewModelScope.launch {
            settings.setMonthlyLimit(limit)
            load()
        }
    }

    /**
     * Forgets the pairing and the limit. Nothing about the money is touched - the account and
     * its transactions are ordinary records that the rest of the app owns.
     */
    private fun removePot() {
        viewModelScope.launch {
            settings.clear()
            state = SharedPotState(loading = false, confirmingRemove = false)
            load()
        }
    }

    private suspend fun load() {
        val config = settings.read()
        val accounts = accountRepository.findAll()
        val options = accounts.map {
            AccountOption(id = it.id.value, name = it.name.value, currency = it.asset.code)
        }.toImmutableList()

        val pot = config.accountId?.let { id -> accounts.firstOrNull { it.id.value == id } }
        if (pot == null || config.monthlyLimit == null) {
            state = state.copy(
                loading = false,
                setUp = false,
                accountOptions = options,
                potName = pot?.name?.value.orEmpty(),
                currency = pot?.asset?.code.orEmpty(),
                limit = config.monthlyLimit ?: 0.0,
            )
            return
        }

        val today = LocalDate.now()
        val monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant()
        val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
            .atTime(23, 59, 59).atZone(zone).toInstant()

        val month = transactionRepository.findAllBetween(monthStart, monthEnd)
            .filter { it.touches(pot.id) }

        state = SharedPotState(
            loading = false,
            setUp = true,
            potName = pot.name.value,
            currency = pot.asset.code,
            limit = config.monthlyLimit,
            spent = month.sumOf { it.spentFrom(pot.id) },
            added = month.sumOf { it.addedTo(pot.id) },
            dayOfMonth = today.dayOfMonth,
            daysInMonth = today.lengthOfMonth(),
            // Today counts as a day you can still spend on, so a limit reached on the last of
            // the month divides by one rather than by zero.
            daysLeft = today.lengthOfMonth() - today.dayOfMonth + 1,
            // Spending only. A pot exists to answer "how much of the limit is left", and a
            // salary credit landing in the same account dwarfs every line that question is
            // about. The money-in total above still accounts for it.
            recent = month.filter { it.spentFrom(pot.id) > 0.0 }
                .sortedByDescending { it.time }
                .take(RECENT_LIMIT)
                .mapNotNull { it.toEntry(pot.id) }
                .toImmutableList(),
            accountOptions = options,
        )
    }

    private fun Transaction.touches(potId: AccountId): Boolean = when (this) {
        is Expense -> account == potId
        is Income -> account == potId
        is Transfer -> fromAccount == potId || toAccount == potId
    }

    /**
     * Money leaving the pot. A transfer out counts: the pot is smaller afterwards, and a rule
     * that only looked at expenses could be walked around by moving money to a personal
     * account first.
     */
    private fun Transaction.spentFrom(potId: AccountId): Double = when (this) {
        is Expense -> if (account == potId) value.amount.value else 0.0
        is Transfer -> if (fromAccount == potId) fromValue.amount.value else 0.0
        else -> 0.0
    }

    private fun Transaction.addedTo(potId: AccountId): Double = when (this) {
        is Income -> if (account == potId) value.amount.value else 0.0
        is Transfer -> if (toAccount == potId) toValue.amount.value else 0.0
        else -> 0.0
    }

    private fun Transaction.toEntry(potId: AccountId): PotEntry? {
        val spent = spentFrom(potId)
        val added = addedTo(potId)
        if (spent == 0.0 && added == 0.0) return null
        return PotEntry(
            id = id.value,
            title = title?.value ?: if (spent > 0) "Spent" else "Added",
            amount = if (spent > 0) spent else added,
            income = spent == 0.0,
            timeLabel = dayFormatter.format(time.atZone(zone)),
        )
    }

    private companion object {
        const val RECENT_LIMIT = 12
        val zone: ZoneId = ZoneId.systemDefault()
        val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
    }
}
