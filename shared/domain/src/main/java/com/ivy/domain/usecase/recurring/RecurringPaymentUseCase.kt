package com.ivy.domain.usecase.recurring

import com.ivy.base.model.TransactionType
import com.ivy.base.threading.DispatchersProvider
import com.ivy.base.time.TimeConverter
import com.ivy.data.db.dao.read.PlannedPaymentRuleDao
import com.ivy.data.db.dao.write.WritePlannedPaymentRuleDao
import com.ivy.data.db.entity.PlannedPaymentRuleEntity
import com.ivy.data.model.Expense
import com.ivy.data.model.IntervalType
import com.ivy.data.repository.TransactionRepository
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

/**
 * A payment that keeps coming back.
 *
 * Detected from the ledger alone - same payee, similar amount, a steady gap - so it works
 * whatever put the transactions there: typed by hand, imported from a CSV, captured from a bank
 * alert. Nothing about it needs to read your messages.
 */
data class RecurringCandidate(
    val payee: String,
    val typicalAmount: Double,
    val assetCode: String,
    val occurrences: Int,
    val intervalDays: Int,
    val intervalType: IntervalType,
    val intervalN: Int,
    val lastSeen: Instant,
    val nextExpected: LocalDate,
    val accountId: UUID,
    val categoryId: UUID?,
)

class RecurringPaymentUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val plannedPaymentRuleDao: PlannedPaymentRuleDao,
    private val writePlannedPaymentRuleDao: WritePlannedPaymentRuleDao,
    private val timeConverter: TimeConverter,
    private val dispatchersProvider: DispatchersProvider,
) {

    /**
     * Groups expenses by payee and keeps the ones that arrive on a rhythm.
     *
     * Deliberately strict: three sightings minimum, amounts within a fifth of each other, and a
     * gap that lands close to a week, a month or a quarter. A detector that guesses generously
     * fills the screen with things that are not subscriptions, and then nobody reads it.
     */
    suspend fun detect(): List<RecurringCandidate> = withContext(dispatchersProvider.io) {
        val alreadyPlanned = plannedPaymentRuleDao.findAllByOneTime(oneTime = false)
            .mapNotNull { it.title?.trim()?.lowercase() }
            .toSet()

        transactionRepository.findAll()
            .filterIsInstance<Expense>()
            .mapNotNull { expense ->
                expense.title?.value?.trim()?.takeIf { it.isNotBlank() }?.let { it to expense }
            }
            .groupBy({ it.first.lowercase() }, { it.second })
            .filterKeys { it !in alreadyPlanned }
            .mapNotNull { (_, expenses) -> candidateOrNull(expenses) }
            .sortedByDescending { it.typicalAmount }
    }

    /**
     * Turns a detected rhythm into a planned payment, which is the app's existing way of saying
     * "this is going to happen again".
     */
    suspend fun trackAsPlannedPayment(
        candidate: RecurringCandidate,
    ): Boolean = withContext(dispatchersProvider.io) {
        val startDate = with(timeConverter) {
            candidate.nextExpected.atStartOfDay().toUTC()
        }

        writePlannedPaymentRuleDao.save(
            PlannedPaymentRuleEntity(
                startDate = startDate,
                intervalN = candidate.intervalN,
                intervalType = candidate.intervalType,
                oneTime = false,
                type = TransactionType.EXPENSE,
                accountId = candidate.accountId,
                amount = candidate.typicalAmount,
                categoryId = candidate.categoryId,
                title = candidate.payee,
                description = DETECTED_NOTE,
            )
        )
        true
    }

    @Suppress("ReturnCount")
    private fun candidateOrNull(expenses: List<Expense>): RecurringCandidate? {
        if (expenses.size < MIN_OCCURRENCES) return null

        val sorted = expenses.sortedBy { it.time }
        val amounts = sorted.map { it.value.amount.value }
        val median = amounts.sorted()[amounts.size / 2]
        if (median <= 0.0) return null

        // A subscription that doubles isn't a subscription, it's two different things sharing
        // a name.
        val consistent = amounts.all { abs(it - median) / median <= AMOUNT_TOLERANCE }
        if (!consistent) return null

        val gaps = sorted.zipWithNext { a, b ->
            Duration.between(a.time, b.time).toDays().toInt()
        }.filter { it > 0 }
        if (gaps.isEmpty()) return null

        val medianGap = gaps.sorted()[gaps.size / 2]
        val cadence = cadenceFor(medianGap) ?: return null

        val last = sorted.last()
        val lastDate = with(timeConverter) { last.time.toLocalDate() }

        return RecurringCandidate(
            payee = last.title?.value.orEmpty(),
            typicalAmount = median,
            assetCode = last.value.asset.code,
            occurrences = sorted.size,
            intervalDays = medianGap,
            intervalType = cadence.first,
            intervalN = cadence.second,
            lastSeen = last.time,
            nextExpected = lastDate.plusDays(medianGap.toLong()),
            accountId = last.account.value,
            categoryId = last.category?.value,
        )
    }

    /**
     * Maps a measured gap onto the intervals the app can actually schedule. Anything that isn't
     * close to one of them is a coincidence, not a subscription.
     */
    private fun cadenceFor(days: Int): Pair<IntervalType, Int>? = when (days) {
        in WEEKLY_RANGE -> IntervalType.WEEK to 1
        in FORTNIGHTLY_RANGE -> IntervalType.WEEK to 2
        in MONTHLY_RANGE -> IntervalType.MONTH to 1
        in QUARTERLY_RANGE -> IntervalType.MONTH to 3
        in YEARLY_RANGE -> IntervalType.YEAR to 1
        else -> null
    }

    companion object {
        const val MIN_OCCURRENCES = 3
        const val AMOUNT_TOLERANCE = 0.2
        const val DETECTED_NOTE = "Detected from your transaction history"

        private val WEEKLY_RANGE = 6..8
        private val FORTNIGHTLY_RANGE = 13..16
        private val MONTHLY_RANGE = 27..34
        private val QUARTERLY_RANGE = 86..95
        private val YEARLY_RANGE = 358..372
    }
}
