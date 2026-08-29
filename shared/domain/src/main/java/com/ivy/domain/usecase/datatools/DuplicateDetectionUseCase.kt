package com.ivy.domain.usecase.datatools

import com.ivy.base.model.TransactionType
import com.ivy.base.threading.DispatchersProvider
import com.ivy.data.model.Expense
import com.ivy.data.model.Income
import com.ivy.data.model.Transaction
import com.ivy.data.model.TransactionId
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.CategoryRepository
import com.ivy.data.repository.TransactionRepository
import com.ivy.domain.WidgetRefresher
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

data class DuplicateCandidate(
    val id: UUID,
    val title: String?,
    val categoryName: String?,
    val accountName: String,
    val amount: Double,
    val assetCode: String,
    val type: TransactionType,
    val time: Instant,
    val hasDescription: Boolean,
)

data class DuplicateGroup(
    val candidates: List<DuplicateCandidate>,
) {
    /**
     * The one to keep by default: the richest record wins - a categorised entry with a note is
     * more likely the one the user actually filled in, and the bare one the accidental repeat.
     */
    val suggestedKeep: DuplicateCandidate
        get() = candidates.maxByOrNull { candidate ->
            listOf(
                candidate.categoryName != null,
                candidate.hasDescription,
                candidate.title != null,
            ).count { it }
        } ?: candidates.first()
}

/**
 * Finds transactions that look like the same spend recorded twice.
 *
 * Auto-capture makes this inevitable: a bank texts, the app files it, and then the user - who
 * doesn't know that yet - types it in as well. Same account, same amount, minutes apart.
 *
 * Nothing is ever merged automatically. Two identical coffees on the same afternoon are a real
 * thing that happens, and a tracker that silently deletes one is worse than one that asks.
 */
class DuplicateDetectionUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val widgetRefresher: WidgetRefresher,
    private val dispatchersProvider: DispatchersProvider,
) {

    suspend fun find(
        withinMinutes: Long = DEFAULT_WINDOW_MINUTES,
    ): List<DuplicateGroup> = withContext(dispatchersProvider.io) {
        val window = Duration.ofMinutes(withinMinutes)
        val categories = categoryRepository.findAll().associate { it.id.value to it.name.value }
        val accounts = accountRepository.findAll().associate { it.id.value to it.name.value }

        transactionRepository.findAll()
            .mapNotNull { it.toCandidate(categories, accounts) }
            .groupBy { candidate ->
                // Same money, same place, same direction - time is what separates a repeat
                // from a duplicate, and that is handled inside the group.
                Triple(candidate.type, candidate.accountName, round(candidate.amount))
            }
            .values
            .flatMap { group -> group.sortedBy { it.time }.cluster(window) }
            .filter { it.candidates.size > 1 }
            .sortedByDescending { group -> group.candidates.maxOf { it.time } }
    }

    /**
     * Keeps one and deletes the rest. Merge rather than "delete duplicates" so the surviving
     * transaction is the user's choice, not the newest by accident.
     */
    suspend fun merge(keepId: UUID, removeIds: List<UUID>) = withContext(dispatchersProvider.io) {
        removeIds.filterNot { it == keepId }
            .forEach { transactionRepository.deleteById(TransactionId(it)) }
        widgetRefresher.refreshAll()
    }

    private fun List<DuplicateCandidate>.cluster(window: Duration): List<DuplicateGroup> {
        val groups = mutableListOf<MutableList<DuplicateCandidate>>()

        forEach { candidate ->
            val current = groups.lastOrNull()
            if (current != null &&
                Duration.between(current.last().time, candidate.time) <= window
            ) {
                current.add(candidate)
            } else {
                groups.add(mutableListOf(candidate))
            }
        }

        return groups.map { DuplicateGroup(candidates = it) }
    }

    private fun Transaction.toCandidate(
        categories: Map<UUID, String>,
        accounts: Map<UUID, String>,
    ): DuplicateCandidate? = when (this) {
        is Expense -> candidate(
            accountId = account.value,
            amount = value.amount.value,
            assetCode = value.asset.code,
            type = TransactionType.EXPENSE,
            categories = categories,
            accounts = accounts,
        )

        is Income -> candidate(
            accountId = account.value,
            amount = value.amount.value,
            assetCode = value.asset.code,
            type = TransactionType.INCOME,
            categories = categories,
            accounts = accounts,
        )

        // Transfers move money between two accounts; a repeated one is a different problem.
        else -> null
    }

    @Suppress("LongParameterList")
    private fun Transaction.candidate(
        accountId: UUID,
        amount: Double,
        assetCode: String,
        type: TransactionType,
        categories: Map<UUID, String>,
        accounts: Map<UUID, String>,
    ) = DuplicateCandidate(
        id = id.value,
        title = title?.value,
        categoryName = category?.value?.let(categories::get),
        accountName = accounts[accountId].orEmpty(),
        amount = amount,
        assetCode = assetCode,
        type = type,
        time = time,
        hasDescription = description != null,
    )

    private fun round(amount: Double): Long = (abs(amount) * ROUNDING).toLong()

    companion object {
        const val DEFAULT_WINDOW_MINUTES = 10L
        private const val ROUNDING = 100
    }
}
