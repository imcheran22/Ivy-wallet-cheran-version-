package com.ivy.domain.usecase.sms

import com.ivy.base.model.TransactionType
import com.ivy.base.threading.DispatchersProvider
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
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Parses a bank SMS and, if it looks like a real transaction, creates it in the wallet.
 *
 * Account matching: tries to match [ParsedBankSms.accountSuffix] against an account's
 * [com.ivy.data.model.Account.bankAccountSuffix]; if nothing matches (most users won't have
 * configured this), it falls back to the account with the lowest `orderNum` (the user's
 * primary/first account) so the transaction still gets created - per product intent, a
 * transaction landing on the "wrong" account is better than the pull silently doing nothing.
 *
 * Category matching: best-effort via [SmsCategoryGuesser]; if no existing category matches
 * the guessed name, the transaction is saved uncategorized rather than guessing wrong.
 */
class ImportSmsTransactionUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val dispatchersProvider: DispatchersProvider,
) {

    sealed interface Result {
        data class Imported(val transaction: Transaction) : Result
        data object NotATransaction : Result
        data object NoAccountsConfigured : Result
        data object InvalidAmount : Result
    }

    suspend fun import(smsBody: String, receivedAt: Instant): Result = withContext(dispatchersProvider.io) {
        val parsed = BankSmsParser.parse(smsBody) ?: return@withContext Result.NotATransaction

        val accounts = accountRepository.findAll()
        if (accounts.isEmpty()) return@withContext Result.NoAccountsConfigured

        val account = accounts.firstOrNull { acc ->
            val suffix = acc.bankAccountSuffix
            val parsedSuffix = parsed.accountSuffix
            !suffix.isNullOrBlank() && !parsedSuffix.isNullOrBlank() &&
                (parsedSuffix.endsWith(suffix) || suffix.endsWith(parsedSuffix))
        } ?: accounts.minByOrNull { it.orderNum } ?: return@withContext Result.NoAccountsConfigured

        val amount = PositiveDouble.from(parsed.amount).getOrNull()
            ?: return@withContext Result.InvalidAmount
        val value = PositiveValue(amount = amount, asset = account.asset)

        val categoryName = SmsCategoryGuesser.guess(parsed.rawText, parsed.counterparty)
        val category = categoryName?.let { name ->
            categoryRepository.findAll().firstOrNull { it.name.value.equals(name, ignoreCase = true) }
        }

        val title = parsed.counterparty
            ?.substringBefore("@")
            ?.let { NotBlankTrimmedString.from(it).getOrNull() }

        val descriptionText = buildString {
            append("Auto-imported from SMS")
            if (parsed.refNo != null) append(" (Ref: ${parsed.refNo})")
        }
        val description = NotBlankTrimmedString.from(descriptionText).getOrNull()

        val metadata = TransactionMetadata(
            recurringRuleId = null,
            paidForDateTime = null,
            loanId = null,
            loanRecordId = null,
        )

        val transaction: Transaction = when (parsed.type) {
            TransactionType.EXPENSE -> Expense(
                id = TransactionId(UUID.randomUUID()),
                title = title,
                description = description,
                category = category?.id,
                time = receivedAt,
                settled = true,
                metadata = metadata,
                tags = emptyList(),
                value = value,
                account = account.id,
            )

            TransactionType.INCOME -> Income(
                id = TransactionId(UUID.randomUUID()),
                title = title,
                description = description,
                category = category?.id,
                time = receivedAt,
                settled = true,
                metadata = metadata,
                tags = emptyList(),
                value = value,
                account = account.id,
            )

            TransactionType.TRANSFER -> return@withContext Result.NotATransaction
        }

        transactionRepository.save(transaction)
        Result.Imported(transaction)
    }
}
