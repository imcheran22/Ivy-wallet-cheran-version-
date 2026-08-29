package com.ivy.domain.usecase.quickadd

import com.ivy.base.model.TransactionType
import com.ivy.base.threading.DispatchersProvider
import com.ivy.base.time.TimeProvider
import com.ivy.data.model.AccountId
import com.ivy.data.model.CategoryId
import com.ivy.data.model.Expense
import com.ivy.data.model.Income
import com.ivy.data.model.PositiveValue
import com.ivy.data.model.Transaction
import com.ivy.data.model.TransactionId
import com.ivy.data.model.TransactionMetadata
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.model.primitive.PositiveDouble
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.CategoryRepository
import com.ivy.data.repository.TransactionRepository
import com.ivy.domain.WidgetRefresher
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Creates a transaction with no UI involved.
 *
 * This is the single write path behind every one-tap entry point - the home screen widgets, the
 * Quick Settings tile, the ongoing notification and the floating sheet. They all reduce to
 * "amount, type, where it came from", and none of them can show a form, so the rules for filling
 * in the blanks live here once instead of being re-guessed in four places.
 *
 * Anything created here is reversible: [add] hands back the id it wrote and [undo] deletes it.
 * That is what makes a single tap safe to be wrong.
 */
class QuickAddTransactionUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val timeProvider: TimeProvider,
    private val widgetRefresher: WidgetRefresher,
    private val dispatchersProvider: DispatchersProvider,
) {

    sealed interface Result {
        /**
         * Carries the names rather than the ids because every caller immediately shows them
         * ("Saved ₹60 · Transport") and most of them - a widget, a notification - have no
         * repository access of their own.
         */
        data class Added(
            val transactionId: UUID,
            val amount: Double,
            val assetCode: String,
            val type: TransactionType,
            val accountName: String,
            val categoryName: String?,
        ) : Result

        data object NoAccounts : Result
        data object InvalidAmount : Result
    }

    @Suppress("LongParameterList", "ReturnCount")
    suspend fun add(
        type: TransactionType,
        amount: Double,
        accountId: UUID? = null,
        categoryId: UUID? = null,
        title: String? = null,
        description: String? = null,
        time: Instant? = null,
    ): Result = withContext(dispatchersProvider.io) {
        if (type == TransactionType.TRANSFER) return@withContext Result.InvalidAmount

        val positiveAmount = PositiveDouble.from(amount).getOrNull()
            ?: return@withContext Result.InvalidAmount

        val account = resolveAccount(accountId) ?: return@withContext Result.NoAccounts
        val category = categoryId
            ?.let { categoryRepository.findById(CategoryId(it)) }

        val transaction = buildTransaction(
            type = type,
            value = PositiveValue(amount = positiveAmount, asset = account.asset),
            accountId = account.id,
            categoryId = category?.id,
            title = title,
            description = description,
            time = time ?: timeProvider.utcNow(),
        )

        transactionRepository.save(transaction)
        widgetRefresher.refreshAll()

        Result.Added(
            transactionId = transaction.id.value,
            amount = amount,
            assetCode = account.asset.code,
            type = type,
            accountName = account.name.value,
            categoryName = category?.name?.value,
        )
    }

    /**
     * Removes a transaction created by [add]. Used by every "Undo" affordance; deliberately
     * silent when the id is already gone so a double-tap can't crash a widget.
     */
    suspend fun undo(transactionId: UUID) = withContext(dispatchersProvider.io) {
        transactionRepository.deleteById(TransactionId(transactionId))
        widgetRefresher.refreshAll()
    }

    /**
     * The account a preset points at, or - when it points at nothing or at something deleted -
     * the user's first account. Same fallback the SMS importer uses: landing on the "wrong"
     * account is recoverable, silently dropping the transaction is not.
     */
    private suspend fun resolveAccount(accountId: UUID?) =
        accountId?.let { accountRepository.findById(AccountId(it)) }
            ?: accountRepository.findAll().minByOrNull { it.orderNum }

    @Suppress("LongParameterList")
    private fun buildTransaction(
        type: TransactionType,
        value: PositiveValue,
        accountId: AccountId,
        categoryId: CategoryId?,
        title: String?,
        description: String?,
        time: Instant,
    ): Transaction {
        val id = TransactionId(UUID.randomUUID())
        val metadata = TransactionMetadata(
            recurringRuleId = null,
            paidForDateTime = null,
            loanId = null,
            loanRecordId = null,
        )
        val titleValue = title?.let { NotBlankTrimmedString.from(it).getOrNull() }
        val descriptionValue = description?.let { NotBlankTrimmedString.from(it).getOrNull() }

        return when (type) {
            TransactionType.EXPENSE -> Expense(
                id = id,
                title = titleValue,
                description = descriptionValue,
                category = categoryId,
                time = time,
                settled = true,
                metadata = metadata,
                tags = emptyList(),
                value = value,
                account = accountId,
            )

            else -> Income(
                id = id,
                title = titleValue,
                description = descriptionValue,
                category = categoryId,
                time = time,
                settled = true,
                metadata = metadata,
                tags = emptyList(),
                value = value,
                account = accountId,
            )
        }
    }
}
