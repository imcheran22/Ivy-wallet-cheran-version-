package com.ivy.domain.usecase.insights

import com.ivy.base.model.LoanRecordType
import com.ivy.base.model.LoanType
import com.ivy.base.threading.DispatchersProvider
import com.ivy.base.time.TimeConverter
import com.ivy.data.db.dao.read.LoanDao
import com.ivy.data.db.dao.read.LoanRecordDao
import com.ivy.data.model.Expense
import com.ivy.data.model.Income
import com.ivy.data.model.Transfer
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.TransactionRepository
import com.ivy.domain.usecase.currency.CurrencyConverter
import com.ivy.domain.usecase.period.MonthPeriodProvider
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * One month's end-of-period position.
 */
data class NetWorthPoint(
    val date: LocalDate,
    val accountsBalance: Double,
    val lentOutstanding: Double,
    val borrowedOutstanding: Double,
) {
    /** Money you have, plus money owed to you, minus money you owe. */
    val netWorth: Double
        get() = accountsBalance + lentOutstanding - borrowedOutstanding
}

data class NetWorthTrend(
    val points: List<NetWorthPoint>,
    val currency: String,
) {
    val current: NetWorthPoint?
        get() = points.lastOrNull()

    /** Change since the first point in the window - the only reason to draw a trend at all. */
    val change: Double
        get() = (points.lastOrNull()?.netWorth ?: 0.0) - (points.firstOrNull()?.netWorth ?: 0.0)
}

/**
 * Net worth month by month, loans included.
 *
 * The app can already say what you have right now. It could never say whether that number has
 * been going up, which is the only version of it that tells you anything.
 *
 * Balances are converted at today's rates rather than each month's - the app doesn't keep
 * historical rates, and inventing them would make the line look precise about something it
 * isn't.
 */
class NetWorthTrendUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val loanDao: LoanDao,
    private val loanRecordDao: LoanRecordDao,
    private val currencyConverter: CurrencyConverter,
    private val periodProvider: MonthPeriodProvider,
    private val timeConverter: TimeConverter,
    private val dispatchersProvider: DispatchersProvider,
) {

    suspend fun load(months: Int = DEFAULT_MONTHS): NetWorthTrend =
        withContext(dispatchersProvider.io) {
            val rates = currencyConverter.rates()
            val accountCurrencies = accountRepository.findAll()
                .associate { it.id.value to it.asset.code }
            val transactions = transactionRepository.findAll().sortedBy { it.time }
            val loans = loanDao.findAll()
            val loanRecords = loanRecordDao.findAll()

            val boundaries = boundaries(months)
            var runningBalance = 0.0
            var index = 0

            val points = boundaries.map { boundary ->
                while (index < transactions.size && transactions[index].time < boundary) {
                    runningBalance += transactions[index].balanceEffect(rates, accountCurrencies)
                    index++
                }

                NetWorthPoint(
                    date = with(timeConverter) { boundary.toLocalDate() },
                    accountsBalance = runningBalance,
                    lentOutstanding = outstanding(
                        loans, loanRecords, LoanType.LEND, boundary, rates, accountCurrencies
                    ),
                    borrowedOutstanding = outstanding(
                        loans, loanRecords, LoanType.BORROW, boundary, rates, accountCurrencies
                    ),
                )
            }

            NetWorthTrend(points = points, currency = rates.base)
        }

    /** One boundary per month end, oldest first, with "now" as the last point. */
    private fun boundaries(months: Int): List<Instant> {
        val current = periodProvider.current()
        return ((months - 1) downTo 1)
            .map { monthsBack -> periodProvider.previous(current, monthsBack.toLong()).endExclusive }
            .plus(current.endExclusive)
    }

    private fun com.ivy.data.model.Transaction.balanceEffect(
        rates: CurrencyConverter.Rates,
        accountCurrencies: Map<UUID, String>,
    ): Double = when (this) {
        is Income -> convert(value.amount.value, account.value, rates, accountCurrencies)
        is Expense -> -convert(value.amount.value, account.value, rates, accountCurrencies)
        is Transfer -> convert(toValue.amount.value, toAccount.value, rates, accountCurrencies) -
            convert(fromValue.amount.value, fromAccount.value, rates, accountCurrencies)
    }

    /**
     * What a loan still stands at on [boundary]: the original amount, less repayments and plus
     * top-ups recorded before then. Loans opened later don't count yet.
     */
    @Suppress("LongParameterList")
    private fun outstanding(
        loans: List<com.ivy.data.db.entity.LoanEntity>,
        records: List<com.ivy.data.db.entity.LoanRecordEntity>,
        type: LoanType,
        boundary: Instant,
        rates: CurrencyConverter.Rates,
        accountCurrencies: Map<UUID, String>,
    ): Double = loans
        .filter { it.type == type }
        .filter { loan ->
            val openedAt = loan.dateTime?.let { with(timeConverter) { it.toUTC() } }
            openedAt == null || openedAt < boundary
        }
        .sumOf { loan ->
            val paid = records
                .filter { it.loanId == loan.id && it.dateTime < boundary && !it.interest }
                .sumOf { record ->
                    val amount = record.convertedAmount ?: record.amount
                    when (record.loanRecordType) {
                        LoanRecordType.DECREASE -> amount
                        LoanRecordType.INCREASE -> -amount
                    }
                }

            val remaining = (loan.amount - paid).coerceAtLeast(0.0)
            convert(remaining, loan.accountId, rates, accountCurrencies)
        }

    private fun convert(
        amount: Double,
        accountId: UUID?,
        rates: CurrencyConverter.Rates,
        accountCurrencies: Map<UUID, String>,
    ): Double {
        val from = accountId?.let(accountCurrencies::get) ?: rates.base
        return rates.convert(amount, from, rates.base) ?: 0.0
    }

    companion object {
        const val DEFAULT_MONTHS = 12
    }
}
