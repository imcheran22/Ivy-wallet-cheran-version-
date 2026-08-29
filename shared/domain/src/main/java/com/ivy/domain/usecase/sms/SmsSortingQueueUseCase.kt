package com.ivy.domain.usecase.sms

import com.ivy.base.model.TransactionType
import com.ivy.base.threading.DispatchersProvider
import com.ivy.data.model.Category
import com.ivy.data.model.CategoryId
import com.ivy.data.model.Expense
import com.ivy.data.model.Income
import com.ivy.data.model.Transaction
import com.ivy.data.model.TransactionId
import com.ivy.data.model.Transfer
import com.ivy.data.model.getFromValue
import com.ivy.data.model.primitive.ColorInt
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.repository.CategoryRepository
import com.ivy.data.repository.TransactionRepository
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * One auto-imported transaction waiting to be told what it was for.
 */
data class SmsInboxItem(
    val transactionId: TransactionId,
    /** Null when the alert named nobody - an ATM withdrawal, say. */
    val payee: String?,
    val amount: Double,
    val assetCode: String,
    val type: TransactionType,
    val time: Instant,
    /** How many transactions in the queue share this payee. Drives the queue order. */
    val timesInQueue: Int,
    val suggestedCategoryId: CategoryId?,
    /** Why [suggestedCategoryId] was pre-selected, in words the user can overrule. */
    val suggestionReason: String?,
)

data class SmsSortingQueue(
    val items: List<SmsInboxItem>,
    /**
     * Total value of everything still unsorted. Shown rather than hidden - a tracker that
     * quietly omits what it doesn't understand is how you end up trusting a wrong total.
     */
    val unsortedExpense: Double,
    val unsortedIncome: Double,
    val assetCode: String?,
)

/**
 * Builds and drains the queue of auto-imported transactions the user still has to identify.
 *
 * The machine can log that ₹340 went to `K MANIKANTA`. It cannot know that is the chai shop
 * downstairs. This is the one piece of work left for a human, so it is designed to shrink:
 * sort by how often a payee recurs, and remember every answer.
 */
class SmsSortingQueueUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val payeeMemory: PayeeMemory,
    private val dispatchersProvider: DispatchersProvider,
) {

    suspend fun load(): SmsSortingQueue = withContext(dispatchersProvider.io) {
        val pending = pendingTransactions()
        val categories = categoryRepository.findAll()

        val payeeCounts = pending
            .groupingBy { PayeeMemory.normalize(it.title?.value) ?: UNNAMED_KEY }
            .eachCount()

        val items = pending
            .map { transaction -> toInboxItem(transaction, categories, payeeCounts) }
            // Naming somewhere you've been to twelve times sorts twelve transactions at once,
            // so the boring part shrinks fastest when the most frequent payee comes first.
            .sortedWith(compareByDescending<SmsInboxItem> { it.timesInQueue }.thenByDescending { it.time })

        SmsSortingQueue(
            items = items,
            unsortedExpense = pending.filterIsInstance<Expense>().sumOf { it.value.amount.value },
            unsortedIncome = pending.filterIsInstance<Income>().sumOf { it.value.amount.value },
            assetCode = pending.firstOrNull()?.getFromValue()?.asset?.code,
        )
    }

    /**
     * Files one transaction. When [rememberPayee] is set, every other queued transaction with
     * the same payee is filed in the same move and the answer is stored for future ones - the
     * user identifies each payee exactly once.
     *
     * Returns how many transactions were categorised.
     */
    suspend fun categorize(
        transactionId: TransactionId,
        categoryId: CategoryId,
        rememberPayee: Boolean,
    ): Int = withContext(dispatchersProvider.io) {
        val pending = pendingTransactions()
        val target = pending.firstOrNull { it.id == transactionId } ?: return@withContext 0
        val payee = PayeeMemory.normalize(target.title?.value)

        val toUpdate = when {
            rememberPayee && payee != null ->
                pending.filter { PayeeMemory.normalize(it.title?.value) == payee }

            else -> listOf(target)
        }

        transactionRepository.saveMany(toUpdate.map { it.withCategory(categoryId) })
        if (rememberPayee && payee != null) {
            payeeMemory.remember(payee, categoryId)
        }
        toUpdate.size
    }

    /**
     * Creates any of [SmsCategories.autoCaptureDefaults] the user doesn't already have, so the
     * suggestions the guesser makes actually have somewhere to land.
     */
    suspend fun createMissingDefaultCategories(): Int = withContext(dispatchersProvider.io) {
        val existing = categoryRepository.findAll()
        val missing = SmsCategories.autoCaptureDefaults.filter { name ->
            existing.none { it.name.value.equals(name, ignoreCase = true) }
        }
        var orderNum = categoryRepository.findMaxOrderNum()
        missing.forEach { name ->
            orderNum += 1.0
            val category = Category(
                id = CategoryId(UUID.randomUUID()),
                name = NotBlankTrimmedString.from(name).getOrNull() ?: return@forEach,
                color = ColorInt(DEFAULT_CATEGORY_COLOR),
                icon = null,
                orderNum = orderNum,
            )
            categoryRepository.save(category)
        }
        missing.size
    }

    private suspend fun pendingTransactions(): List<Transaction> {
        val now = Instant.now()
        return transactionRepository.findAllBetween(
            startDate = now.minus(QUEUE_LOOKBACK),
            endDate = now.plus(QUEUE_LOOKAHEAD),
        ).filter { it.category == null && SmsTransactionMarker.isAutoImported(it.description?.value) }
    }

    private fun toInboxItem(
        transaction: Transaction,
        categories: List<Category>,
        payeeCounts: Map<String, Int>,
    ): SmsInboxItem {
        val payee = transaction.title?.value
        val type = when (transaction) {
            is Income -> TransactionType.INCOME
            is Expense -> TransactionType.EXPENSE
            is Transfer -> TransactionType.TRANSFER
        }
        val amount = transaction.getFromValue().amount.value
        val guess = SmsCategoryGuesser.guessFromRecord(
            payee = payee,
            amount = amount,
            type = type,
            paidToPerson = SmsTransactionMarker.paidToPerson(transaction.description?.value),
        )
        val suggested = guess?.let { g ->
            categories.firstOrNull { it.name.value.equals(g.categoryName, ignoreCase = true) }
        }
        return SmsInboxItem(
            transactionId = transaction.id,
            payee = payee,
            amount = amount,
            assetCode = transaction.getFromValue().asset.code,
            type = type,
            time = transaction.time,
            timesInQueue = payeeCounts[PayeeMemory.normalize(payee) ?: UNNAMED_KEY] ?: 1,
            suggestedCategoryId = suggested?.id,
            suggestionReason = guess?.reason?.takeIf { suggested != null },
        )
    }

    private fun Transaction.withCategory(categoryId: CategoryId): Transaction = when (this) {
        is Income -> copy(category = categoryId)
        is Expense -> copy(category = categoryId)
        is Transfer -> copy(category = categoryId)
    }

    companion object {
        private const val UNNAMED_KEY = "__unnamed__"

        @Suppress("MagicNumber")
        private val DEFAULT_CATEGORY_COLOR = 0xFF9E9E9E.toInt()

        private val QUEUE_LOOKBACK: Duration = Duration.ofDays(180)
        private val QUEUE_LOOKAHEAD: Duration = Duration.ofDays(1)
    }
}
