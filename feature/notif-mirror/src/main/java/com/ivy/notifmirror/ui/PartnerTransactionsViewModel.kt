package com.ivy.notifmirror.ui

import androidx.lifecycle.ViewModel
import com.ivy.notifmirror.sync.PartnerTransaction
import com.ivy.notifmirror.sync.PartnerTransactionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * One category's share of the spending, ready to draw.
 *
 * [share] is precomputed rather than left to the screen: the bar and the percentage have to
 * agree, and the only way to guarantee that is for both to read the same number.
 */
data class CategorySlice(
    val name: String,
    val amount: Double,
    val share: Float,
    val count: Int,
)

data class DayGroup(
    val label: String,
    val total: Double,
    val transactions: ImmutableList<PartnerTransaction>,
)

data class PartnerUiState(
    val transactions: ImmutableList<PartnerTransaction> = persistentListOf(),
    val days: ImmutableList<DayGroup> = persistentListOf(),
    val categories: ImmutableList<CategorySlice> = persistentListOf(),
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val mainCurrency: String = "",
    val biggestExpense: PartnerTransaction? = null,
    val uncategorisedCount: Int = 0,
)

@HiltViewModel
class PartnerTransactionsViewModel @Inject constructor(
    private val store: PartnerTransactionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(PartnerUiState())
    val state: StateFlow<PartnerUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val transactions = store.loadAll()
        val expenses = transactions.filter { it.type == EXPENSE }
        val expenseTotal = expenses.sumOf { it.amount }

        _state.value = PartnerUiState(
            transactions = transactions.toImmutableList(),
            days = groupByDay(transactions.take(ACTIVITY_LIMIT)),
            categories = sliceByCategory(expenses, expenseTotal),
            totalIncome = transactions.filter { it.type == INCOME }.sumOf { it.amount },
            totalExpense = expenseTotal,
            mainCurrency = transactions.firstOrNull()?.currency.orEmpty(),
            biggestExpense = expenses.maxByOrNull { it.amount },
            uncategorisedCount = expenses.count { it.category.isBlank() },
        )
    }

    fun clearAll() {
        store.clear()
        _state.value = PartnerUiState()
    }

    /**
     * Categories are ranked by spend, not alphabetically. The question this screen exists to
     * answer is "where is the money going", and that answer is the top of a sorted list.
     */
    private fun sliceByCategory(
        expenses: List<PartnerTransaction>,
        total: Double,
    ): ImmutableList<CategorySlice> {
        if (expenses.isEmpty()) return persistentListOf()
        return expenses
            .groupBy { it.category.ifBlank { "Uncategorised" } }
            .map { (name, group) ->
                val amount = group.sumOf { it.amount }
                CategorySlice(
                    name = name,
                    amount = amount,
                    share = if (total > 0) (amount / total).toFloat() else 0f,
                    count = group.size,
                )
            }
            .sortedByDescending { it.amount }
            .toImmutableList()
    }

    private fun groupByDay(transactions: List<PartnerTransaction>): ImmutableList<DayGroup> {
        val today = LocalDate.now()
        return transactions
            .sortedByDescending { it.dateTime }
            .groupBy { Instant.ofEpochMilli(it.dateTime).atZone(ZoneId.systemDefault()).toLocalDate() }
            .map { (date, group) ->
                DayGroup(
                    label = dayLabel(date, today),
                    // Income offsets spending, so a day that only received money reads as a
                    // gain rather than as a zero.
                    total = group.sumOf { if (it.type == INCOME) it.amount else -it.amount },
                    transactions = group.toImmutableList(),
                )
            }
            .toImmutableList()
    }

    private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.dayOfMonth.toString() + " " +
            date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(MONTH_ABBREV)
    }

    private companion object {
        const val INCOME = "INCOME"
        const val EXPENSE = "EXPENSE"
        const val MONTH_ABBREV = 3

        /**
         * The activity list renders in a plain scrolling column, so it is capped. The totals
         * and the category breakdown above it still read every stored transaction - it is only
         * the row-by-row tail that is trimmed, and nobody scrolls 500 rows anyway.
         */
        const val ACTIVITY_LIMIT = 120
    }
}
