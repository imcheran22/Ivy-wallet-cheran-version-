package com.ivy.insights

import androidx.compose.runtime.Immutable
import com.ivy.domain.usecase.insights.CategorySpend
import com.ivy.domain.usecase.insights.NetWorthPoint
import com.ivy.domain.usecase.insights.PayeeTotal
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class InsightsState(
    val loading: Boolean = true,
    val currency: String = "",
    val periodLabel: String = "",
    val currentSpent: Double = 0.0,
    val previousSpent: Double = 0.0,
    val currentIncome: Double = 0.0,
    val previousIncome: Double = 0.0,
    val categories: ImmutableList<CategorySpend> = persistentListOf(),
    val topPayees: ImmutableList<PayeeTotal> = persistentListOf(),
    val netWorthPoints: ImmutableList<NetWorthPoint> = persistentListOf(),
    val netWorthNow: Double = 0.0,
    val netWorthChange: Double = 0.0,
) {
    val spentDelta: Double
        get() = currentSpent - previousSpent

    val saved: Double
        get() = currentIncome - currentSpent
}

sealed interface InsightsEvent {
    data object Refresh : InsightsEvent
}
