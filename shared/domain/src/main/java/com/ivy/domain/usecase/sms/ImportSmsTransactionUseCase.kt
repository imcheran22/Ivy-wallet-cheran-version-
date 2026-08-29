package com.ivy.domain.usecase.sms

import com.ivy.base.model.TransactionType
import com.ivy.base.threading.DispatchersProvider
import com.ivy.data.model.AccountId
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
    private val captureLog: SmsCaptureLog,
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

        /** Older than the user's chosen start date, so deliberately out of scope. */
        data object BeforeImportWindow : Result
        data object NoAccountsConfigured : Result
        data object InvalidAmount : Result
    }

    @Suppress("ReturnCount")
    suspend fun import(
        smsBody: String,
        receivedAt: Instant,
    ): Result = withContext(dispatchersProvider.io) {
        val parsed = BankSmsParser.parse(smsBody) ?: return@withContext Result.NotATransaction
        if (!captureLog.isWithinImportWindow(receivedAt)) {
            return@withContext Result.BeforeImportWindow
        }

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

        duplicateOf(parsed, account.id, amount.value, receivedAt)?.let { existing ->
            enrich(existing, parsed)
            return@withContext Result.AlreadyImported
        }

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
     * Finds an already-imported transaction that is the *same payment* as this alert.
     *
     * Two layers, because banks describe one payment in more than one way. The first is the
     * alert's own key, which catches a re-delivered broadcast or a sweep re-reading a message
     * already captured - identical text, identical key.
     *
     * The second exists because that is not enough. A single credit routinely arrives as two
     * different messages: one naming the remitter ("NEFT-UTIB0000009-MANIPAL HEALTH ENTER-AX")
     * and one that only states the amount and balance. Different text hashes to a different
     * key, so key-matching alone files the money twice. What actually identifies a payment is
     * its economics: same account, same direction, same amount, minutes apart.
     *
     * The guard against over-merging is the reference number. Banks issue a distinct one per
     * payment, so two alerts that both carry a ref and disagree on it are two real payments -
     * paying the same shop the same amount twice in an hour stays two rows. Only where at
     * least one side has no ref does the economic match decide.
     */
    private suspend fun duplicateOf(
        parsed: ParsedBankSms,
        accountId: AccountId,
        amount: Double,
        receivedAt: Instant,
    ): Transaction? {
        val window = transactionRepository.findAllBetween(
            startDate = receivedAt.minus(DEDUPE_LOOKBACK),
            endDate = receivedAt.plus(DEDUPE_LOOKAHEAD),
        )

        val imported = window.filter { SmsTransactionMarker.isAutoImported(it.description?.value) }

        imported.firstOrNull {
            SmsTransactionMarker.dedupeKeyOf(it.description?.value) == parsed.dedupeKey
        }?.let { return it }

        return imported.firstOrNull { candidate ->
            val economics = economicsOf(candidate) ?: return@firstOrNull false
            economics.accountId == accountId &&
                economics.type == parsed.type &&
                economics.amount == amount &&
                withinSamePaymentWindow(candidate.time, receivedAt) &&
                !refNosDisagree(
                    SmsTransactionMarker.dedupeKeyOf(candidate.description?.value),
                    parsed.dedupeKey,
                )
        }
    }

    /**
     * The duplicate is not always the poorer of the two. Where the message that arrived second
     * names the payee and the stored row has no title, keeping the better name costs one write
     * and is the difference between a readable ledger and a column of bare amounts.
     */
    private suspend fun enrich(existing: Transaction, parsed: ParsedBankSms) {
        if (existing.title != null) return
        val title = parsed.payee?.let { NotBlankTrimmedString.from(it).getOrNull() } ?: return
        val updated = when (existing) {
            is Expense -> existing.copy(title = title)
            is Income -> existing.copy(title = title)
            else -> return
        }
        transactionRepository.save(updated)
    }

    private data class Economics(
        val accountId: AccountId,
        val amount: Double,
        val type: TransactionType,
    )

    private fun economicsOf(transaction: Transaction): Economics? = when (transaction) {
        is Income -> Economics(transaction.account, transaction.value.amount.value, TransactionType.INCOME)
        is Expense -> Economics(transaction.account, transaction.value.amount.value, TransactionType.EXPENSE)
        else -> null
    }

    private fun withinSamePaymentWindow(a: Instant, b: Instant): Boolean {
        val apart = kotlin.math.abs(a.toEpochMilli() - b.toEpochMilli())
        return apart <= SAME_PAYMENT_WINDOW.toMillis()
    }

    /**
     * True only when both keys are reference-number keys and the references differ - the one
     * case where identical economics still mean two separate payments.
     */
    private fun refNosDisagree(existingKey: String?, newKey: String): Boolean {
        val existingRef = existingKey?.takeIf { it.startsWith(REF_KEY_PREFIX) } ?: return false
        val newRef = newKey.takeIf { it.startsWith(REF_KEY_PREFIX) } ?: return false
        return existingRef != newRef
    }

    companion object {
        private val DEDUPE_LOOKBACK: Duration = Duration.ofDays(3)
        private val DEDUPE_LOOKAHEAD: Duration = Duration.ofDays(1)

        /**
         * How far apart the two halves of one payment can land. A bank's second message
         * usually follows within seconds, but a delayed SMS can arrive an hour or more later;
         * three hours covers that without spanning a plausible repeat purchase.
         */
        private val SAME_PAYMENT_WINDOW: Duration = Duration.ofHours(3)

        private const val REF_KEY_PREFIX = "ref-"
    }
}
