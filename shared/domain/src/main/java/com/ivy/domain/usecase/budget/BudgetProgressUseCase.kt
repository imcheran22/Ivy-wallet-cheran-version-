package com.ivy.domain.usecase.budget

import com.ivy.base.threading.DispatchersProvider
import com.ivy.base.time.TimeProvider
import com.ivy.data.db.dao.read.BudgetDao
import com.ivy.data.db.entity.BudgetEntity
import com.ivy.data.model.Expense
import com.ivy.data.model.Transaction
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.TransactionRepository
import com.ivy.domain.usecase.currency.CurrencyConverter
import com.ivy.domain.usecase.period.MonthPeriod
import com.ivy.domain.usecase.period.MonthPeriodProvider
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max

/**
 * What one budget has left this month.
 *
 * [rollover] is the unspent remainder carried in from earlier months - real envelope budgeting,
 * where an under-spent February makes March genuinely roomier instead of the surplus vanishing
 * at midnight on the 1st.
 */
data class BudgetSummary(
    val id: UUID,
    val name: String,
    val amount: Double,
    val spent: Double,
    val rolloverEnabled: Boolean,
    val rollover: Double,
    val categoryIds: List<UUID>,
) {
    val available: Double
        get() = amount + rollover

    val remaining: Double
        get() = available - spent

    val overspent: Boolean
        get() = remaining < 0
}

/**
 * The whole month at a glance, including the one number that actually changes behaviour:
 * [safeToSpendToday].
 */
data class BudgetProgress(
    val budgets: List<BudgetSummary>,
    val totalBudgeted: Double,
    val totalSpent: Double,
    val currency: String,
    val period: MonthPeriod,
    val daysLeft: Int,
) {
    val remaining: Double
        get() = totalBudgeted - totalSpent

    /**
     * What's left, spread evenly over the days that are left. Beats a monthly total because a
     * monthly total tells you nothing on the 3rd and everything on the 28th.
     */
    val safeToSpendToday: Double
        get() = if (daysLeft <= 0) remaining else remaining / daysLeft

    val hasBudgets: Boolean
        get() = budgets.isNotEmpty()
}

class BudgetProgressUseCase @Inject constructor(
    private val budgetDao: BudgetDao,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val currencyConverter: CurrencyConverter,
    private val periodProvider: MonthPeriodProvider,
    private val budgetPreferences: BudgetPreferences,
    private val timeProvider: TimeProvider,
    private val dispatchersProvider: DispatchersProvider,
) {

    suspend fun load(): BudgetProgress = withContext(dispatchersProvider.io) {
        val period = periodProvider.current()
        val budgets = budgetDao.findAll()
        val rates = currencyConverter.rates()
        val accountCurrencies = accountRepository.findAll()
            .associate { it.id.value to it.asset.code }
        val rolloverIds = budgetPreferences.rolloverBudgetIds()

        // One read covers this month plus everything rollover needs to look back at.
        val historyStart = periodProvider.previous(period, ROLLOVER_MONTHS.toLong()).start
        val transactions = transactionRepository.findAllBetween(historyStart, period.endExclusive)

        val summaries = budgets.map { budget ->
            summarize(
                budget = budget,
                transactions = transactions,
                period = period,
                rates = rates,
                accountCurrencies = accountCurrencies,
                rolloverEnabled = budget.id in rolloverIds,
            )
        }

        BudgetProgress(
            budgets = summaries,
            totalBudgeted = totalBudgeted(summaries),
            totalSpent = totalSpent(summaries),
            currency = rates.base,
            period = period,
            daysLeft = period.daysLeft(timeProvider.localDateNow()),
        )
    }

    /**
     * An overall budget (one with no categories) is a cap on everything, so it can't simply be
     * added to the per-category ones - that would count the same money twice. When the user has
     * both, the overall budget wins as the month's headline number.
     */
    private fun totalBudgeted(summaries: List<BudgetSummary>): Double {
        val overall = summaries.filter { it.categoryIds.isEmpty() }
        return if (overall.isNotEmpty()) {
            overall.sumOf { it.available }
        } else {
            summaries.sumOf { it.available }
        }
    }

    private fun totalSpent(summaries: List<BudgetSummary>): Double {
        val overall = summaries.filter { it.categoryIds.isEmpty() }
        return if (overall.isNotEmpty()) {
            overall.sumOf { it.spent }
        } else {
            summaries.sumOf { it.spent }
        }
    }

    @Suppress("LongParameterList")
    private fun summarize(
        budget: BudgetEntity,
        transactions: List<Transaction>,
        period: MonthPeriod,
        rates: CurrencyConverter.Rates,
        accountCurrencies: Map<UUID, String>,
        rolloverEnabled: Boolean,
    ): BudgetSummary {
        val categoryIds = parseIds(budget.categoryIdsSerialized)
        val accountIds = parseIds(budget.accountIdsSerialized)

        val matching = transactions.filterIsInstance<Expense>()
            .filter { accountIds.isEmpty() || it.account.value in accountIds }
            .filter { categoryIds.isEmpty() || it.category?.value in categoryIds }

        val spent = matching
            .filter { it.time >= period.start && it.time < period.endExclusive }
            .sumOf { it.toBase(rates, accountCurrencies) }

        return BudgetSummary(
            id = budget.id,
            name = budget.name,
            amount = budget.amount,
            spent = spent,
            rolloverEnabled = rolloverEnabled,
            rollover = if (rolloverEnabled) {
                rollover(budget.amount, matching, period, rates, accountCurrencies)
            } else {
                0.0
            },
            categoryIds = categoryIds,
        )
    }

    /**
     * Sums what was left over in each of the previous [ROLLOVER_MONTHS] months.
     *
     * Overspending in a past month subtracts, so the carried balance stays honest, but the total
     * is floored at zero: a budget can start the month with extra room, never with a debt the
     * user has no way to see or clear.
     */
    private fun rollover(
        amount: Double,
        matching: List<Expense>,
        period: MonthPeriod,
        rates: CurrencyConverter.Rates,
        accountCurrencies: Map<UUID, String>,
    ): Double {
        var carried = 0.0
        for (monthsBack in 1..ROLLOVER_MONTHS) {
            val past = periodProvider.previous(period, monthsBack.toLong())
            val spent = matching
                .filter { it.time >= past.start && it.time < past.endExclusive }
                .sumOf { it.toBase(rates, accountCurrencies) }
            carried += amount - spent
        }
        return max(0.0, carried)
    }

    private fun Expense.toBase(
        rates: CurrencyConverter.Rates,
        accountCurrencies: Map<UUID, String>,
    ): Double {
        val from = accountCurrencies[account.value] ?: rates.base
        return rates.convert(value.amount.value, from, rates.base) ?: 0.0
    }

    private fun parseIds(serialized: String?): List<UUID> = serialized
        ?.split(",")
        ?.mapNotNull { runCatching { UUID.fromString(it.trim()) }.getOrNull() }
        .orEmpty()

    companion object {
        /** How far back rollover accumulates. A year is plenty and keeps the query bounded. */
        const val ROLLOVER_MONTHS = 12
    }
}
