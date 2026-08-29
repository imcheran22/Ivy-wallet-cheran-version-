package com.ivy.domain.usecase.summary

import com.ivy.base.threading.DispatchersProvider
import com.ivy.base.time.TimeConverter
import com.ivy.base.time.TimeProvider
import com.ivy.data.model.Expense
import com.ivy.data.model.Income
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.TransactionRepository
import com.ivy.domain.usecase.currency.CurrencyConverter
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * What happened today, in one line.
 *
 * The interesting number isn't the total - it's [uncategorizedCount]. Auto-captured transactions
 * arrive with an amount and a payee but no meaning, and sorting them is a two-minute job on the
 * day and an archaeology project a month later.
 */
data class DailySummary(
    val date: LocalDate,
    val spent: Double,
    val received: Double,
    val currency: String,
    val transactionCount: Int,
    val uncategorizedCount: Int,
) {
    val isEmpty: Boolean
        get() = transactionCount == 0
}

class DailySummaryUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val currencyConverter: CurrencyConverter,
    private val timeProvider: TimeProvider,
    private val timeConverter: TimeConverter,
    private val dispatchersProvider: DispatchersProvider,
) {

    suspend fun forDate(date: LocalDate): DailySummary = withContext(dispatchersProvider.io) {
        val (start, end) = with(timeConverter) {
            date.atStartOfDay().toUTC() to date.plusDays(1).atStartOfDay().toUTC()
        }
        val rates = currencyConverter.rates()
        val accountCurrencies: Map<UUID, String> = accountRepository.findAll()
            .associate { it.id.value to it.asset.code }

        val transactions = transactionRepository.findAllBetween(start, end)
            .filter { it.time >= start && it.time < end }

        var spent = 0.0
        var received = 0.0
        var uncategorized = 0

        transactions.forEach { transaction ->
            when (transaction) {
                is Expense -> {
                    spent += convert(
                        transaction.value.amount.value,
                        accountCurrencies[transaction.account.value],
                        rates,
                    )
                    if (transaction.category == null) uncategorized++
                }

                is Income -> {
                    received += convert(
                        transaction.value.amount.value,
                        accountCurrencies[transaction.account.value],
                        rates,
                    )
                    if (transaction.category == null) uncategorized++
                }

                else -> Unit // transfers move money, they don't spend it
            }
        }

        DailySummary(
            date = date,
            spent = spent,
            received = received,
            currency = rates.base,
            transactionCount = transactions.size,
            uncategorizedCount = uncategorized,
        )
    }

    suspend fun today(): DailySummary = forDate(timeProvider.localDateNow())

    private fun convert(
        amount: Double,
        from: String?,
        rates: CurrencyConverter.Rates,
    ): Double = rates.convert(amount, from ?: rates.base, rates.base) ?: 0.0
}
