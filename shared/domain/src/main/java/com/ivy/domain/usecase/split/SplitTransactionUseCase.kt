package com.ivy.domain.usecase.split

import com.ivy.data.model.LoanType
import com.ivy.base.threading.DispatchersProvider
import com.ivy.base.time.TimeProvider
import com.ivy.data.db.dao.write.WriteLoanDao
import com.ivy.data.db.entity.LoanEntity
import com.ivy.data.model.CategoryId
import com.ivy.data.model.Expense
import com.ivy.data.model.Income
import com.ivy.data.model.PositiveValue
import com.ivy.data.model.Transaction
import com.ivy.data.model.TransactionId
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.model.primitive.PositiveDouble
import com.ivy.data.repository.TransactionRepository
import com.ivy.domain.WidgetRefresher
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

/**
 * One share of a split bill.
 *
 * A share with [owedBy] set isn't your spending at all - it's money someone owes you, so it
 * leaves the ledger as a loan rather than as a transaction of yours.
 */
data class SplitPart(
    val amount: Double,
    val categoryId: UUID? = null,
    val title: String? = null,
    val owedBy: String? = null,
)

/**
 * Breaks one transaction into parts.
 *
 * The remainder stays on the original transaction rather than the split consuming it, because
 * that matches how people describe a bill: "this ₹1,200 dinner - ₹300 of it was Ravi's". Your
 * share is what's left, and the transaction you were already looking at is still there.
 */
class SplitTransactionUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val writeLoanDao: WriteLoanDao,
    private val timeProvider: TimeProvider,
    private val widgetRefresher: WidgetRefresher,
    private val dispatchersProvider: DispatchersProvider,
) {

    sealed interface Result {
        data class Split(val createdTransactions: Int, val createdLoans: Int) : Result
        data object NotFound : Result
        data object NothingToSplit : Result
        data object PartsExceedTotal : Result
    }

    @Suppress("ReturnCount")
    suspend fun split(
        transactionId: UUID,
        parts: List<SplitPart>,
    ): Result = withContext(dispatchersProvider.io) {
        val valid = parts.filter { it.amount > 0.0 }
        if (valid.isEmpty()) return@withContext Result.NothingToSplit

        val original = transactionRepository.findById(TransactionId(transactionId))
            ?: return@withContext Result.NotFound

        val originalAmount = original.amountOrNull() ?: return@withContext Result.NotFound
        val partsTotal = valid.sumOf { it.amount }
        if (partsTotal >= originalAmount) return@withContext Result.PartsExceedTotal

        val remainder = PositiveDouble.from(originalAmount - partsTotal).getOrNull()
            ?: return@withContext Result.PartsExceedTotal

        var createdTransactions = 0
        var createdLoans = 0

        valid.forEach { part ->
            if (part.owedBy != null) {
                createLoan(part, original)
                createdLoans++
            } else {
                createSplitTransaction(part, original)?.let {
                    transactionRepository.save(it)
                    createdTransactions++
                }
            }
        }

        transactionRepository.save(original.withAmount(remainder))
        widgetRefresher.refreshAll()

        Result.Split(createdTransactions = createdTransactions, createdLoans = createdLoans)
    }

    private suspend fun createLoan(part: SplitPart, original: Transaction) {
        writeLoanDao.save(
            LoanEntity(
                name = part.owedBy.orEmpty(),
                amount = part.amount,
                type = LoanType.LEND,
                accountId = original.accountIdOrNull(),
                dateTime = LocalDateTime.now(timeProvider.getZoneId()),
                note = part.title ?: original.title?.value,
            )
        )
    }

    private fun createSplitTransaction(part: SplitPart, original: Transaction): Transaction? {
        val amount = PositiveDouble.from(part.amount).getOrNull() ?: return null
        val title = (part.title ?: original.title?.value)
            ?.let { NotBlankTrimmedString.from(it).getOrNull() }
        val id = TransactionId(UUID.randomUUID())

        return when (original) {
            is Expense -> original.copy(
                id = id,
                title = title,
                category = part.categoryId?.let(::CategoryId) ?: original.category,
                value = PositiveValue(amount = amount, asset = original.value.asset),
                tags = emptyList(),
            )

            is Income -> original.copy(
                id = id,
                title = title,
                category = part.categoryId?.let(::CategoryId) ?: original.category,
                value = PositiveValue(amount = amount, asset = original.value.asset),
                tags = emptyList(),
            )

            else -> null
        }
    }

    private fun Transaction.amountOrNull(): Double? = when (this) {
        is Expense -> value.amount.value
        is Income -> value.amount.value
        else -> null
    }

    private fun Transaction.accountIdOrNull(): UUID? = when (this) {
        is Expense -> account.value
        is Income -> account.value
        else -> null
    }

    private fun Transaction.withAmount(amount: PositiveDouble): Transaction = when (this) {
        is Expense -> copy(value = value.copy(amount = amount))
        is Income -> copy(value = value.copy(amount = amount))
        else -> this
    }
}
