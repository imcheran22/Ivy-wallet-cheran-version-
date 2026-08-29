package com.ivy.domain.usecase.quickadd

import com.ivy.base.model.TransactionType
import com.ivy.base.threading.DispatchersProvider
import com.ivy.data.model.Category
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
import com.ivy.domain.usecase.sms.PayeeMemory
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Files a transaction from one typed line, with no screen involved.
 *
 * The point is that nothing here can ask a question. A quick add happens on a lock screen while
 * paying for something, so every decision has to have an answer already: the account is the
 * primary one, and the category is whatever the payee was sorted into last time - the same
 * memory the SMS queue writes to, so naming the chai shop once teaches both routes at once.
 * Anything unanswered stays uncategorised and visible rather than guessed at.
 */
class QuickAddTransactionUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val payeeMemory: PayeeMemory,
    private val dispatchersProvider: DispatchersProvider,
) {

    sealed interface Result {
        data class Added(
            val amount: Double,
            val title: String?,
            val currency: String,
            val type: TransactionType,
        ) : Result

        data object NotUnderstood : Result
        data object NoAccountsConfigured : Result
    }

    suspend fun add(raw: String): Result = withContext(dispatchersProvider.io) {
        val parsed = QuickAddParser.parse(raw) ?: return@withContext Result.NotUnderstood

        val account = accountRepository.findAll().minByOrNull { it.orderNum }
            ?: return@withContext Result.NoAccountsConfigured

        val amount = PositiveDouble.from(parsed.amount).getOrNull()
            ?: return@withContext Result.NotUnderstood
        val value = PositiveValue(amount = amount, asset = account.asset)

        val title = parsed.title?.let { NotBlankTrimmedString.from(it).getOrNull() }
        val categories = categoryRepository.findAll()
        val category = rememberedCategory(parsed.title, categories)

        val metadata = TransactionMetadata(
            recurringRuleId = null,
            paidForDateTime = null,
            loanId = null,
            loanRecordId = null,
        )

        val transaction: Transaction = when (parsed.type) {
            TransactionType.INCOME -> Income(
                id = TransactionId(UUID.randomUUID()),
                title = title,
                description = null,
                category = category,
                time = Instant.now(),
                settled = true,
                metadata = metadata,
                tags = emptyList(),
                value = value,
                account = account.id,
            )

            else -> Expense(
                id = TransactionId(UUID.randomUUID()),
                title = title,
                description = null,
                category = category,
                time = Instant.now(),
                settled = true,
                metadata = metadata,
                tags = emptyList(),
                value = value,
                account = account.id,
            )
        }

        transactionRepository.save(transaction)

        Result.Added(
            amount = parsed.amount,
            title = parsed.title,
            currency = account.asset.code,
            type = parsed.type,
        )
    }

    private suspend fun rememberedCategory(
        payee: String?,
        categories: List<Category>,
    ): CategoryId? {
        val remembered = payeeMemory.categoryFor(payee) ?: return null
        return remembered.takeIf { id -> categories.any { it.id == id } }
    }
}
