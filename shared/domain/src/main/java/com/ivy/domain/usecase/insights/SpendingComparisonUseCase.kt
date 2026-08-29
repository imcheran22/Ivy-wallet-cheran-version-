package com.ivy.domain.usecase.insights

import com.ivy.base.threading.DispatchersProvider
import com.ivy.data.model.Expense
import com.ivy.data.model.Income
import com.ivy.data.model.Transaction
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.CategoryRepository
import com.ivy.data.repository.TransactionRepository
import com.ivy.domain.usecase.currency.CurrencyConverter
import com.ivy.domain.usecase.period.MonthPeriod
import com.ivy.domain.usecase.period.MonthPeriodProvider
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

/**
 * One category, this month against last.
 */
data class CategorySpend(
    val categoryId: UUID?,
    val name: String,
    val color: Int,
    val current: Double,
    val previous: Double,
) {
    val delta: Double
        get() = current - previous

    /**
     * Null when there's nothing to compare against - a category with no spend last month grew
     * from nothing, and "+∞%" is not a useful thing to tell someone.
     */
    val percentChange: Double?
        get() = if (previous > 0.0) (delta / previous) * PERCENT else null

    val isNotable: Boolean
        get() = abs(delta) > 0.0

    companion object {
        private const val PERCENT = 100.0
    }
}

data class PayeeTotal(
    val name: String,
    val amount: Double,
    val count: Int,
)

data class SpendingComparison(
    val currency: String,
    val period: MonthPeriod,
    val currentSpent: Double,
    val previousSpent: Double,
    val currentIncome: Double,
    val previousIncome: Double,
    val categories: List<CategorySpend>,
    val topPayees: List<PayeeTotal>,
) {
    val spentDelta: Double
        get() = currentSpent - previousSpent

    val savedThisMonth: Double
        get() = currentIncome - currentSpent
}

/**
 * This month against last, by category and by payee.
 *
 * A total on its own is inert - ₹6,200 on food means nothing until you know it was ₹4,500 last
 * month. Comparison is what turns a record into a signal.
 */
class SpendingComparisonUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val currencyConverter: CurrencyConverter,
    private val periodProvider: MonthPeriodProvider,
    private val dispatchersProvider: DispatchersProvider,
) {

    suspend fun load(): SpendingComparison = withContext(dispatchersProvider.io) {
        val current = periodProvider.current()
        val previous = periodProvider.previous(current, 1)

        val rates = currencyConverter.rates()
        val accountCurrencies = accountRepository.findAll()
            .associate { it.id.value to it.asset.code }
        val categories = categoryRepository.findAll()

        val transactions = transactionRepository
            .findAllBetween(previous.start, current.endExclusive)

        val currentTrns = transactions.inPeriod(current)
        val previousTrns = transactions.inPeriod(previous)

        val currentByCategory = currentTrns.expenseByCategory(rates, accountCurrencies)
        val previousByCategory = previousTrns.expenseByCategory(rates, accountCurrencies)

        val categoryNames = categories.associate { it.id.value to (it.name.value to it.color.value) }

        val comparison = (currentByCategory.keys + previousByCategory.keys).map { categoryId ->
            val nameAndColor = categoryId?.let(categoryNames::get)
            CategorySpend(
                categoryId = categoryId,
                name = nameAndColor?.first ?: UNCATEGORIZED,
                color = nameAndColor?.second ?: 0,
                current = currentByCategory[categoryId] ?: 0.0,
                previous = previousByCategory[categoryId] ?: 0.0,
            )
        }
            .filter { it.isNotable }
            // Biggest movement first: that's the thing worth doing something about.
            .sortedByDescending { abs(it.delta) }

        SpendingComparison(
            currency = rates.base,
            period = current,
            currentSpent = currentByCategory.values.sum(),
            previousSpent = previousByCategory.values.sum(),
            currentIncome = currentTrns.incomeTotal(rates, accountCurrencies),
            previousIncome = previousTrns.incomeTotal(rates, accountCurrencies),
            categories = comparison,
            topPayees = currentTrns.topPayees(rates, accountCurrencies),
        )
    }

    private fun List<Transaction>.inPeriod(period: MonthPeriod) =
        filter { it.time >= period.start && it.time < period.endExclusive }

    private fun List<Transaction>.expenseByCategory(
        rates: CurrencyConverter.Rates,
        accountCurrencies: Map<UUID, String>,
    ): Map<UUID?, Double> = filterIsInstance<Expense>()
        .groupBy { it.category?.value }
        .mapValues { (_, expenses) ->
            expenses.sumOf { it.convert(rates, accountCurrencies) }
        }

    private fun List<Transaction>.incomeTotal(
        rates: CurrencyConverter.Rates,
        accountCurrencies: Map<UUID, String>,
    ): Double = filterIsInstance<Income>().sumOf { income ->
        val from = accountCurrencies[income.account.value] ?: rates.base
        rates.convert(income.value.amount.value, from, rates.base) ?: 0.0
    }

    /**
     * Groups by transaction title, which is where a payee name ends up - typed by hand or
     * captured from a bank alert. Unnamed spends are left out rather than lumped together.
     */
    private fun List<Transaction>.topPayees(
        rates: CurrencyConverter.Rates,
        accountCurrencies: Map<UUID, String>,
    ): List<PayeeTotal> = filterIsInstance<Expense>()
        .mapNotNull { expense ->
            expense.title?.value?.trim()?.takeIf { it.isNotBlank() }?.let { title ->
                title to expense.convert(rates, accountCurrencies)
            }
        }
        .groupBy({ it.first.lowercase() }, { it })
        .map { (_, entries) ->
            PayeeTotal(
                name = entries.first().first,
                amount = entries.sumOf { it.second },
                count = entries.size,
            )
        }
        .sortedByDescending { it.amount }
        .take(TOP_PAYEES)

    private fun Expense.convert(
        rates: CurrencyConverter.Rates,
        accountCurrencies: Map<UUID, String>,
    ): Double {
        val from = accountCurrencies[account.value] ?: rates.base
        return rates.convert(value.amount.value, from, rates.base) ?: 0.0
    }

    companion object {
        const val TOP_PAYEES = 5
        const val UNCATEGORIZED = "Uncategorized"
    }
}
