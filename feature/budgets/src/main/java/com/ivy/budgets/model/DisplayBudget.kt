package com.ivy.budgets.model

import androidx.compose.runtime.Immutable
import com.ivy.legacy.datamodel.Budget
import com.ivy.wallet.domain.data.Reorderable

@Immutable
data class DisplayBudget(
    val budget: Budget,
    val spentAmount: Double,
    /**
     * Unspent money carried in from previous months. Zero unless the user turned rollover on
     * for this budget.
     */
    val rollover: Double = 0.0,
    val rolloverEnabled: Boolean = false,
) : Reorderable {
    /** What's actually available this month: the budget plus whatever it carried in. */
    val availableAmount: Double
        get() = budget.amount + rollover

    override fun getItemOrderNum(): Double {
        return budget.orderId
    }

    override fun withNewOrderNum(newOrderNum: Double): Reorderable {
        return this.copy(
            budget = budget.copy(
                orderId = newOrderNum
            )
        )
    }
}
