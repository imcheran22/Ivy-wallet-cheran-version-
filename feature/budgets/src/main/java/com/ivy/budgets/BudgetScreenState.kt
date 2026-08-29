package com.ivy.budgets

import com.ivy.budgets.model.DisplayBudget
import com.ivy.data.model.Category
import com.ivy.legacy.data.model.FromToTimeRange
import com.ivy.legacy.datamodel.Account
import kotlinx.collections.immutable.ImmutableList
import javax.annotation.concurrent.Immutable

@Immutable
data class BudgetScreenState(
    val baseCurrency: String,
    val budgets: ImmutableList<DisplayBudget>,
    val categories: ImmutableList<Category>,
    val accounts: ImmutableList<Account>,
    val categoryBudgetsTotal: Double,
    val appBudgetMax: Double,
    val totalRemainingBudgetText: String?,
    val timeRange: FromToTimeRange?,
    val reorderModalVisible: Boolean,
    val budgetModalData: BudgetModalData?,
    /**
     * What's left divided by the days left - the number that changes a decision in a shop,
     * where a monthly total doesn't.
     */
    val safeToSpendToday: Double = 0.0,
    val daysLeft: Int = 0,
)
