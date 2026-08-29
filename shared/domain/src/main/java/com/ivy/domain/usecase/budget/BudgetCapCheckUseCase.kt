package com.ivy.domain.usecase.budget

import com.ivy.base.threading.DispatchersProvider
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/**
 * What a transaction being typed right now would do to the budget it lands in.
 *
 * Told at the moment of entry, this changes a decision. Told in a report next week, it's
 * just a fact about the past.
 */
data class BudgetCapCheck(
    val budgetName: String,
    val remainingAfter: Double,
    val currency: String,
    /** True when the transaction takes the budget past its limit. */
    val exceeded: Boolean,
)

class BudgetCapCheckUseCase @Inject constructor(
    private val budgetProgressUseCase: BudgetProgressUseCase,
    private val dispatchersProvider: DispatchersProvider,
) {

    /**
     * @return the tightest budget this transaction touches, or null when it touches none.
     * The tightest one is the one worth warning about: clearing that leaves the others fine.
     */
    suspend fun check(
        amount: Double,
        categoryId: UUID?,
        accountId: UUID?,
    ): BudgetCapCheck? = withContext(dispatchersProvider.io) {
        if (amount <= 0.0) return@withContext null

        val progress = runCatching { budgetProgressUseCase.load() }.getOrNull()
            ?: return@withContext null

        progress.budgets
            .filter { budget -> budget.matches(categoryId, accountId) }
            .minByOrNull { it.remaining }
            ?.let { budget ->
                BudgetCapCheck(
                    budgetName = budget.name,
                    remainingAfter = budget.remaining - amount,
                    currency = progress.currency,
                    exceeded = budget.remaining - amount < 0,
                )
            }
    }

    /**
     * A budget with no categories caps everything, so it matches whatever the user is entering;
     * one with categories only matches its own.
     */
    private fun BudgetSummary.matches(categoryId: UUID?, accountId: UUID?): Boolean {
        val categoryMatches = categoryIds.isEmpty() || (categoryId != null && categoryId in categoryIds)
        val accountMatches = accountIds.isEmpty() || (accountId != null && accountId in accountIds)
        return categoryMatches && accountMatches
    }
}
