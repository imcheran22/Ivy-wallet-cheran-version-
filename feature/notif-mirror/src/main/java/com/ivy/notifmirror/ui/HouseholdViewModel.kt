package com.ivy.notifmirror.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.domain.usecase.budget.BudgetProgress
import com.ivy.domain.usecase.budget.BudgetProgressUseCase
import com.ivy.domain.usecase.currency.CurrencyConverter
import com.ivy.domain.usecase.household.SettleUpUseCase
import com.ivy.domain.usecase.household.Settlement
import com.ivy.domain.usecase.insights.SpendingComparisonUseCase
import com.ivy.domain.usecase.period.MonthPeriodProvider
import com.ivy.domain.usecase.quickadd.QuickAddOptionsUseCase
import com.ivy.notifmirror.sync.PartnerTransaction
import com.ivy.notifmirror.sync.PartnerTransactionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * One category, both people.
 */
@Immutable
data class HouseholdCategory(
    val name: String,
    val yours: Double,
    val partners: Double,
) {
    val combined: Double
        get() = yours + partners
}

/**
 * A budget measured against what the household spent, not just what you spent.
 *
 * This is what makes a shared budget work without a shared server: the budget lives on one
 * phone, but the spending it is judged against comes from both.
 */
@Immutable
data class HouseholdBudget(
    val name: String,
    val amount: Double,
    val yours: Double,
    val partners: Double,
) {
    val combined: Double
        get() = yours + partners

    val remaining: Double
        get() = amount - combined

    val overspent: Boolean
        get() = remaining < 0
}

@Immutable
data class HouseholdState(
    val loading: Boolean = true,
    val currency: String = "",
    val periodLabel: String = "",
    val yourSpend: Double = 0.0,
    val partnerSpend: Double = 0.0,
    val categories: ImmutableList<HouseholdCategory> = persistentListOf(),
    val budgets: ImmutableList<HouseholdBudget> = persistentListOf(),
    val settlement: Settlement? = null,
    val settlementRecorded: Boolean = false,
    val partnerName: String = DEFAULT_PARTNER_NAME,
) {
    val combinedSpend: Double
        get() = yourSpend + partnerSpend

    companion object {
        const val DEFAULT_PARTNER_NAME = "Partner"
    }
}

sealed interface HouseholdEvent {
    data object Refresh : HouseholdEvent
    data object RecordSettlement : HouseholdEvent
    data class SetPartnerName(val name: String) : HouseholdEvent
}

@HiltViewModel
class HouseholdViewModel @Inject constructor(
    private val store: PartnerTransactionStore,
    private val spendingComparisonUseCase: SpendingComparisonUseCase,
    private val budgetProgressUseCase: BudgetProgressUseCase,
    private val settleUpUseCase: SettleUpUseCase,
    private val optionsUseCase: QuickAddOptionsUseCase,
    private val currencyConverter: CurrencyConverter,
    private val periodProvider: MonthPeriodProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(HouseholdState())
    val state: StateFlow<HouseholdState> = _state.asStateFlow()

    init {
        onEvent(HouseholdEvent.Refresh)
    }

    fun onEvent(event: HouseholdEvent) {
        when (event) {
            HouseholdEvent.Refresh -> refresh()
            HouseholdEvent.RecordSettlement -> recordSettlement()
            is HouseholdEvent.SetPartnerName -> _state.update {
                it.copy(partnerName = event.name.ifBlank { HouseholdState.DEFAULT_PARTNER_NAME })
            }
        }
    }

    private fun recordSettlement() {
        val state = _state.value
        val settlement = state.settlement ?: return

        viewModelScope.launch {
            val recorded = settleUpUseCase.record(
                partnerName = state.partnerName,
                settlement = settlement,
            )
            _state.update { it.copy(settlementRecorded = recorded) }
        }
    }

    @Suppress("LongMethod")
    private fun refresh() {
        viewModelScope.launch {
            val period = periodProvider.current()
            val rates = runCatching { currencyConverter.rates() }.getOrNull()
            val comparison = runCatching { spendingComparisonUseCase.load() }.getOrNull()
            val progress = runCatching { budgetProgressUseCase.load() }.getOrNull()
            val categoryIdsByName = runCatching { optionsUseCase.load() }.getOrNull()
                ?.categories
                ?.associate { it.name.lowercase() to it.id }
                .orEmpty()

            val baseCurrency = comparison?.currency ?: rates?.base.orEmpty()

            // Only the partner spending that falls inside the same month the rest of this
            // screen is talking about, converted into the currency the totals are shown in.
            val partnerExpenses = store.loadAll()
                .filter { it.type == EXPENSE }
                .filter {
                    it.dateTime >= period.start.toEpochMilli() &&
                        it.dateTime < period.endExclusive.toEpochMilli()
                }

            val partnerAmounts = partnerExpenses.associateWith { transaction ->
                partnerAmountInBase(transaction, rates, baseCurrency)
            }

            val partnerByCategory = partnerExpenses
                .groupBy { it.category.ifBlank { UNCATEGORIZED } }
                .mapValues { (_, group) -> group.sumOf { partnerAmounts[it] ?: 0.0 } }

            val yourByCategory = comparison?.categories.orEmpty()
                .associate { it.name to it.current }

            val categories = (yourByCategory.keys + partnerByCategory.keys)
                .map { name ->
                    HouseholdCategory(
                        name = name,
                        yours = yourByCategory[name] ?: 0.0,
                        partners = partnerByCategory[name] ?: 0.0,
                    )
                }
                .sortedByDescending { it.combined }

            val yourSpend = comparison?.currentSpent ?: 0.0
            val partnerSpend = partnerAmounts.values.sum()

            _state.update { state ->
                state.copy(
                    loading = false,
                    currency = baseCurrency,
                    periodLabel = period.label(),
                    yourSpend = yourSpend,
                    partnerSpend = partnerSpend,
                    categories = categories.toImmutableList(),
                    budgets = householdBudgets(
                        progress = progress,
                        partnerExpenses = partnerExpenses,
                        partnerAmounts = partnerAmounts,
                        categoryIdsByName = categoryIdsByName,
                    ).toImmutableList(),
                    settlement = Settlement(
                        yourSpend = yourSpend,
                        partnerSpend = partnerSpend,
                        currency = baseCurrency,
                    ),
                    settlementRecorded = false,
                )
            }
        }
    }

    /**
     * Partner amounts arrive tagged with their own currency, which may not be yours - a shared
     * total that quietly adds euros to rupees would be worse than showing nothing.
     */
    private fun partnerAmountInBase(
        transaction: PartnerTransaction,
        rates: CurrencyConverter.Rates?,
        baseCurrency: String,
    ): Double {
        if (rates == null || transaction.currency.isBlank()) return transaction.amount
        return rates.convert(transaction.amount, transaction.currency, baseCurrency)
            ?: transaction.amount
    }

    /**
     * Matches the partner's spending onto your budgets by category name, which is all the
     * mirrored feed carries. A budget with no categories caps everything, so it takes all of it.
     */
    private fun householdBudgets(
        progress: BudgetProgress?,
        partnerExpenses: List<PartnerTransaction>,
        partnerAmounts: Map<PartnerTransaction, Double>,
        categoryIdsByName: Map<String, UUID>,
    ): List<HouseholdBudget> = progress?.budgets.orEmpty().map { budget ->
        val partnerShare = partnerExpenses
            .filter { transaction ->
                budget.categoryIds.isEmpty() ||
                    categoryIdsByName[transaction.category.lowercase()] in budget.categoryIds
            }
            .sumOf { partnerAmounts[it] ?: 0.0 }

        HouseholdBudget(
            name = budget.name,
            amount = budget.available,
            yours = budget.spent,
            partners = partnerShare,
        )
    }

    companion object {
        private const val EXPENSE = "EXPENSE"
        private const val UNCATEGORIZED = "Uncategorized"
    }
}
