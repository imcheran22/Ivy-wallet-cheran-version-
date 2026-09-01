package com.ivy.wallet.domain.deprecated.logic

import com.ivy.base.legacy.Transaction
import com.ivy.base.time.TimeProvider
import com.ivy.data.db.dao.read.PlannedPaymentRuleDao
import com.ivy.data.repository.TransactionRepository
import com.ivy.data.repository.mapper.TransactionMapper
import com.ivy.legacy.datamodel.PlannedPaymentRule
import com.ivy.legacy.datamodel.temp.toDomain
import com.ivy.legacy.datamodel.temp.toLegacyDomain
import com.ivy.legacy.incrementDate
import java.time.Instant
import javax.inject.Inject

class PlannedPaymentsGenerator @Inject constructor(
    private val transactionMapper: TransactionMapper,
    private val transactionRepository: TransactionRepository,
    private val plannedPaymentRuleDao: PlannedPaymentRuleDao,
    private val timeProvider: TimeProvider,
) {
    companion object {
        private const val GENERATED_INSTANCES_LIMIT = 72

        /** Three years, in seconds. */
        private const val GENERATION_HORIZON_SECONDS = 94_608_000L

        /**
         * How few unpaid instances a recurring rule may be left with before it gets topped up.
         * For a daily rule this is about a week of runway.
         */
        private const val MIN_PENDING_INSTANCES = 8
    }

    suspend fun generate(rule: PlannedPaymentRule) {
        // delete all not happened transactions
        transactionRepository.deletedByRecurringRuleIdAndNoDateTime(
            recurringRuleId = rule.id
        )

        if (rule.oneTime) {
            generateOneTime(rule)
        } else {
            generateRecurring(rule)
        }
    }

    /**
     * Refills recurring rules that are running out of upcoming instances.
     *
     * Instances are only ever generated [GENERATED_INSTANCES_LIMIT] at a time, which is decades
     * for a yearly rule but barely two months for a daily one. Without this, a daily payment
     * silently stops appearing once its batch is used up.
     */
    suspend fun topUpRecurring() {
        plannedPaymentRuleDao.findAllByOneTime(oneTime = false)
            .map { it.toLegacyDomain() }
            .filter { it.startDate != null && it.intervalType != null && (it.intervalN ?: 0) > 0 }
            .forEach { rule ->
                // Overdue instances count as runway too - a rule the user simply hasn't paid
                // yet isn't running dry, and regenerating it would churn its transaction ids.
                val pending = transactionRepository.findAllByRecurringRuleId(rule.id)
                    .count { !it.settled }

                if (pending < MIN_PENDING_INSTANCES) {
                    generate(rule)
                }
            }
    }

    private suspend fun generateOneTime(rule: PlannedPaymentRule) {
        val trns = transactionRepository.findAllByRecurringRuleId(recurringRuleId = rule.id)

        if (trns.isEmpty()) {
            generateTransaction(rule, rule.startDate!!)
        }
    }

    private suspend fun generateRecurring(rule: PlannedPaymentRule) {
        val startDate = rule.startDate!!

        // Anchored to today rather than to the rule's start date: a daily rule created a few
        // years ago would otherwise run past its own window and never produce another instance.
        val endDate = maxOf(startDate, timeProvider.utcNow())
            .plusSeconds(GENERATION_HORIZON_SECONDS)

        val trns = transactionRepository.findAllByRecurringRuleId(recurringRuleId = rule.id)
        var trnsToSkip = trns.size

        var generatedTransactions = 0

        var date = startDate
        while (date.isBefore(endDate)) {
            if (generatedTransactions >= GENERATED_INSTANCES_LIMIT) {
                break
            }

            if (trnsToSkip > 0) {
                // skip first N happened transactions
                trnsToSkip--
            } else {
                // generate transaction
                generateTransaction(
                    rule = rule,
                    dueDate = date
                )
                generatedTransactions++
            }

            val intervalN = rule.intervalN!!.toLong()
            date = rule.intervalType!!.incrementDate(
                date = date,
                intervalN = intervalN
            )
        }
    }

    private suspend fun generateTransaction(rule: PlannedPaymentRule, dueDate: Instant) {
        Transaction(
            type = rule.type,
            accountId = rule.accountId,
            recurringRuleId = rule.id,
            categoryId = rule.categoryId,
            amount = rule.amount.toBigDecimal(),
            title = rule.title,
            description = rule.description,
            dueDate = dueDate,
            dateTime = null,
            toAccountId = null,
            isSynced = false
        ).toDomain(transactionMapper)?.let {
            transactionRepository.save(it)
        }
    }
}
