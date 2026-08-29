package com.ivy.domain.usecase.datatools

import com.ivy.base.model.TransactionType
import com.ivy.base.threading.DispatchersProvider
import com.ivy.data.model.CategoryId
import com.ivy.data.model.Expense
import com.ivy.data.model.Income
import com.ivy.data.model.Transaction
import com.ivy.data.model.TransactionId
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.CategoryRepository
import com.ivy.data.repository.TransactionRepository
import com.ivy.domain.WidgetRefresher
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class BulkTransactionRow(
    val id: UUID,
    val title: String?,
    val categoryName: String?,
    val accountName: String,
    val amount: Double,
    val assetCode: String,
    val type: TransactionType,
    val time: Instant,
)

/**
 * Which transactions to work on.
 *
 * [categoryId] null with [onlyUncategorized] false means "any category" - the distinction
 * matters because uncategorized is the case people actually need to fix in bulk.
 */
data class BulkFilter(
    val categoryId: UUID? = null,
    val onlyUncategorized: Boolean = false,
    val query: String = "",
    val limit: Int = DEFAULT_LIMIT,
) {
    companion object {
        const val DEFAULT_LIMIT = 200
    }
}

/**
 * Recategorises many transactions at once.
 *
 * Fixing forty miscategorised transactions one screen at a time is forty screens, which is why
 * nobody does it and why a month of data stays wrong.
 */
class BulkEditUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val widgetRefresher: WidgetRefresher,
    private val dispatchersProvider: DispatchersProvider,
) {

    suspend fun find(filter: BulkFilter): List<BulkTransactionRow> =
        withContext(dispatchersProvider.io) {
            val categories = categoryRepository.findAll().associate { it.id.value to it.name.value }
            val accounts = accountRepository.findAll().associate { it.id.value to it.name.value }
            val query = filter.query.trim().lowercase()

            transactionRepository.findAll()
                .asSequence()
                .filter { it is Expense || it is Income }
                .filter { transaction ->
                    when {
                        filter.onlyUncategorized -> transaction.category == null
                        filter.categoryId != null -> transaction.category?.value == filter.categoryId
                        else -> true
                    }
                }
                .filter { transaction ->
                    query.isBlank() ||
                        transaction.title?.value?.lowercase()?.contains(query) == true
                }
                .sortedByDescending { it.time }
                .take(filter.limit)
                .mapNotNull { it.toRow(categories, accounts) }
                .toList()
        }

    suspend fun recategorize(
        transactionIds: List<UUID>,
        categoryId: UUID?,
    ): Int = withContext(dispatchersProvider.io) {
        if (transactionIds.isEmpty()) return@withContext 0

        val updated = transactionIds.mapNotNull { id ->
            transactionRepository.findById(TransactionId(id))?.withCategory(categoryId)
        }

        transactionRepository.saveMany(updated)
        widgetRefresher.refreshAll()
        updated.size
    }

    private fun Transaction.withCategory(categoryId: UUID?): Transaction? {
        val category = categoryId?.let(::CategoryId)
        return when (this) {
            is Expense -> copy(category = category)
            is Income -> copy(category = category)
            else -> null
        }
    }

    private fun Transaction.toRow(
        categories: Map<UUID, String>,
        accounts: Map<UUID, String>,
    ): BulkTransactionRow? = when (this) {
        is Expense -> row(
            accountId = account.value,
            amount = value.amount.value,
            assetCode = value.asset.code,
            type = TransactionType.EXPENSE,
            categories = categories,
            accounts = accounts,
        )

        is Income -> row(
            accountId = account.value,
            amount = value.amount.value,
            assetCode = value.asset.code,
            type = TransactionType.INCOME,
            categories = categories,
            accounts = accounts,
        )

        else -> null
    }

    @Suppress("LongParameterList")
    private fun Transaction.row(
        accountId: UUID,
        amount: Double,
        assetCode: String,
        type: TransactionType,
        categories: Map<UUID, String>,
        accounts: Map<UUID, String>,
    ) = BulkTransactionRow(
        id = id.value,
        title = title?.value,
        categoryName = category?.value?.let(categories::get),
        accountName = accounts[accountId].orEmpty(),
        amount = amount,
        assetCode = assetCode,
        type = type,
        time = time,
    )
}
