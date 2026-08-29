package com.ivy.domain.usecase.recent

import com.ivy.base.model.TransactionType
import com.ivy.base.threading.DispatchersProvider
import com.ivy.base.time.TimeProvider
import com.ivy.data.model.Expense
import com.ivy.data.model.Income
import com.ivy.data.model.Transfer
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.CategoryRepository
import com.ivy.data.repository.TransactionRepository
import kotlinx.coroutines.withContext
import java.time.Duration
import java.util.UUID
import javax.inject.Inject

/**
 * A row in the recent-transactions widget, already reduced to strings.
 *
 * The widget can't reach the repositories, format money or resolve a category id, so all of that
 * happens here - what crosses the boundary is what gets drawn.
 */
data class RecentTransactionRow(
    val id: UUID,
    val type: TransactionType,
    val title: String,
    val categoryName: String?,
    val accountName: String,
    val amount: Double,
    val assetCode: String,
    val timeEpochMillis: Long,
    val uncategorized: Boolean,
)

class RecentTransactionsUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val timeProvider: TimeProvider,
    private val dispatchersProvider: DispatchersProvider,
) {

    suspend fun load(limit: Int = DEFAULT_LIMIT): List<RecentTransactionRow> =
        withContext(dispatchersProvider.io) {
            val now = timeProvider.utcNow()
            val from = now.minus(Duration.ofDays(LOOKBACK_DAYS))
            val categories = categoryRepository.findAll().associate { it.id.value to it.name.value }
            val accounts = accountRepository.findAll().associate { it.id.value to it.name.value }

            transactionRepository.findAllBetween(from, now)
                .sortedByDescending { it.time }
                .take(limit)
                .map { transaction ->
                    val categoryName = transaction.category?.value?.let(categories::get)
                    when (transaction) {
                        is Expense -> row(
                            id = transaction.id.value,
                            type = TransactionType.EXPENSE,
                            title = transaction.title?.value,
                            categoryName = categoryName,
                            accountName = accounts[transaction.account.value],
                            amount = transaction.value.amount.value,
                            assetCode = transaction.value.asset.code,
                            time = transaction.time,
                        )

                        is Income -> row(
                            id = transaction.id.value,
                            type = TransactionType.INCOME,
                            title = transaction.title?.value,
                            categoryName = categoryName,
                            accountName = accounts[transaction.account.value],
                            amount = transaction.value.amount.value,
                            assetCode = transaction.value.asset.code,
                            time = transaction.time,
                        )

                        is Transfer -> row(
                            id = transaction.id.value,
                            type = TransactionType.TRANSFER,
                            title = transaction.title?.value,
                            categoryName = categoryName,
                            accountName = accounts[transaction.fromAccount.value],
                            amount = transaction.fromValue.amount.value,
                            assetCode = transaction.fromValue.asset.code,
                            time = transaction.time,
                        )
                    }
                }
        }

    @Suppress("LongParameterList")
    private fun row(
        id: UUID,
        type: TransactionType,
        title: String?,
        categoryName: String?,
        accountName: String?,
        amount: Double,
        assetCode: String,
        time: java.time.Instant,
    ) = RecentTransactionRow(
        id = id,
        type = type,
        title = title ?: categoryName ?: accountName.orEmpty(),
        categoryName = categoryName,
        accountName = accountName.orEmpty(),
        amount = amount,
        assetCode = assetCode,
        timeEpochMillis = time.toEpochMilli(),
        uncategorized = categoryName == null && type != TransactionType.TRANSFER,
    )

    companion object {
        const val DEFAULT_LIMIT = 10
        const val LOOKBACK_DAYS = 60L
    }
}
