package com.ivy.domain.usecase.trip

import com.ivy.base.threading.DispatchersProvider
import com.ivy.base.time.TimeConverter
import com.ivy.data.model.Expense
import com.ivy.data.model.Income
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.CategoryRepository
import com.ivy.data.repository.TransactionRepository
import com.ivy.domain.usecase.currency.CurrencyConverter
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

data class TripCategorySpend(
    val name: String,
    val color: Int,
    val amount: Double,
)

data class TripSummary(
    val trip: Trip,
    val baseCurrency: String,
    val spent: Double,
    val received: Double,
    /** The same total in the currency the money was actually spent in, when the trip names one. */
    val spentInTripCurrency: Double?,
    val transactionCount: Int,
    val categories: List<TripCategorySpend>,
) {
    val perDay: Double
        get() = if (trip.days > 0) spent / trip.days else spent

    val net: Double
        get() = spent - received
}

/**
 * Adds up a trip.
 *
 * Multi-currency is the whole point: everything is converted to the home currency so the totals
 * are comparable, and reported again in the trip's own currency because "₹42,000" and
 * "€460" answer different questions.
 */
class TripSummaryUseCase @Inject constructor(
    private val tripStore: TripStore,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val currencyConverter: CurrencyConverter,
    private val timeConverter: TimeConverter,
    private val dispatchersProvider: DispatchersProvider,
) {

    suspend fun summarize(trip: Trip): TripSummary = withContext(dispatchersProvider.io) {
        val start = with(timeConverter) { trip.startDate.atStartOfDay().toUTC() }
        val end = with(timeConverter) {
            trip.endDateInclusive.plusDays(1).atStartOfDay().toUTC()
        }

        val rates = currencyConverter.rates()
        val accountCurrencies = accountRepository.findAll()
            .associate { it.id.value to it.asset.code }
        val categoryNames = categoryRepository.findAll()
            .associate { it.id.value to (it.name.value to it.color.value) }

        val transactions = transactionRepository.findAllBetween(start, end)
            .filter { it.time >= start && it.time < end }

        val expenses = transactions.filterIsInstance<Expense>()
            .filter { trip.accountIds.isEmpty() || it.account.value in trip.accountIds }
        val incomes = transactions.filterIsInstance<Income>()
            .filter { trip.accountIds.isEmpty() || it.account.value in trip.accountIds }

        val spent = expenses.sumOf { it.toBase(rates, accountCurrencies) }

        TripSummary(
            trip = trip,
            baseCurrency = rates.base,
            spent = spent,
            received = incomes.sumOf { income ->
                val from = accountCurrencies[income.account.value] ?: rates.base
                rates.convert(income.value.amount.value, from, rates.base) ?: 0.0
            },
            spentInTripCurrency = trip.currency
                ?.takeIf { !it.equals(rates.base, ignoreCase = true) }
                ?.let { rates.convert(spent, rates.base, it) },
            transactionCount = expenses.size,
            categories = expenses
                .groupBy { it.category?.value }
                .map { (categoryId, group) ->
                    val nameAndColor = categoryId?.let(categoryNames::get)
                    TripCategorySpend(
                        name = nameAndColor?.first ?: UNCATEGORIZED,
                        color = nameAndColor?.second ?: 0,
                        amount = group.sumOf { it.toBase(rates, accountCurrencies) },
                    )
                }
                .sortedByDescending { it.amount },
        )
    }

    suspend fun summarizeAll(): List<TripSummary> = tripStore.all().map { summarize(it) }

    private fun Expense.toBase(
        rates: CurrencyConverter.Rates,
        accountCurrencies: Map<UUID, String>,
    ): Double {
        val from = accountCurrencies[account.value] ?: rates.base
        return rates.convert(value.amount.value, from, rates.base) ?: 0.0
    }

    companion object {
        const val UNCATEGORIZED = "Uncategorized"
    }
}
