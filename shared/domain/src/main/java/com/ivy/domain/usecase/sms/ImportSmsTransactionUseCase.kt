package com.ivy.domain.usecase.sms

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
import kotlinx.coroutines.withContext
import java.time.Duration
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
 * Only one thing files a transaction automatically: an answer the user has already given. If
 * they have sorted this payee before, reuse that category - naming the chai shop downstairs
 * today categorises every future payment to it.
 *
 * A [SmsCategoryGuesser] guess deliberately does *not* file anything. It travels back in
 * [Result.Imported.guess] and is pre-selected in the sorting queue instead, next to the reason
 * it was made, so the user can overrule it. A guess that files itself is a guess nobody ever
 * audits, which is worse than no guess at all - and the "paid to a person, typical fare" rule
 * in particular is wrong often enough that it has to be seen.
 *
 * So anything the user hasn't answered for lands in the queue uncategorized. Uncategorized is
 * a visible state here, not a hidden one.
 */
class ImportSmsTransactionUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val payeeMemory: PayeeMemory,
    private val dispatchersProvider: DispatchersProvider,
) {

    sealed interface Result {
        data class Imported(
            val transaction: Transaction,
            val parsed: ParsedBankSms,
            val guess: SmsCategoryGuess?,
        ) : Result

        data object NotATransaction : Result
        data object AlreadyImported : Result
        data object NoAccountsConfigured : Result
        data object InvalidAmount : Result
    }

    @Suppress("ReturnCount")
    suspend fun import(
        smsBody: String,
        receivedAt: Instant,
    ): Result = withContext(dispatchersProvider.io) {
        val parsed = BankSmsParser.parse(smsBody) ?: return@withContext Result.NotATransaction
        if (isAlreadyImported(parsed.dedupeKey, receivedAt)) return@withContext Result.AlreadyImported

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

        val categories = categoryRepository.findAll()
        val guess = SmsCategoryGuesser.guess(parsed)
        val categoryId = rememberedCategory(parsed.payee, categories)

        val metadata = TransactionMetadata(
            recurringRuleId = null,
            paidForDateTime = null,
            loanId = null,
            loanRecordId = null,
        )
        val title = parsed.payee?.let { NotBlankTrimmedString.from(it).getOrNull() }
        val description = NotBlankTrimmedString.from(
            SmsTransactionMarker.describe(
                refNo = parsed.refNo,
                dedupeKey = parsed.dedupeKey,
                paidToPerson = parsed.paidToPerson,
            )
        ).getOrNull()

        val transaction: Transaction = when (parsed.type) {
            TransactionType.EXPENSE -> Expense(
                id = TransactionId(UUID.randomUUID()),
                title = title,
                description = description,
                category = categoryId,
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
                category = categoryId,
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
        Result.Imported(transaction = transaction, parsed = parsed, guess = guess)
    }

    /**
     * A remembered category is only honoured while the category still exists - otherwise a
     * category the user deleted would keep quietly swallowing new transactions.
     */
    private suspend fun rememberedCategory(
        payee: String?,
        categories: List<Category>,
    ): CategoryId? {
        val remembered = payeeMemory.categoryFor(payee) ?: return null
        return remembered.takeIf { id -> categories.any { it.id == id } }
    }

    /**
     * Dedupe on the alert's own key rather than on "have I seen this thread before". Labelling
     * a Gmail-style conversation, or any per-sender marker, would skip every later alert that
     * shares it - all of a bank's alerts look alike by design.
     */
    private suspend fun isAlreadyImported(dedupeKey: String, receivedAt: Instant): Boolean {
        val window = transactionRepository.findAllBetween(
            startDate = receivedAt.minus(DEDUPE_LOOKBACK),
            endDate = receivedAt.plus(DEDUPE_LOOKAHEAD),
        )
        return window.any { SmsTransactionMarker.dedupeKeyOf(it.description?.value) == dedupeKey }
    }

    companion object {
        private val DEDUPE_LOOKBACK: Duration = Duration.ofDays(3)
        private val DEDUPE_LOOKAHEAD: Duration = Duration.ofDays(1)
    }
}
