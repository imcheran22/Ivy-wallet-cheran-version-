package com.ivy.insights

import androidx.compose.runtime.Immutable
import com.ivy.domain.usecase.insights.CategorySpend
import com.ivy.domain.usecase.insights.NetWorthPoint
import com.ivy.domain.usecase.insights.PayeeTotal
import com.ivy.domain.usecase.recurring.RecurringCandidate
import android.net.Uri
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

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
    /** Payments that keep coming back, found in the ledger rather than in your messages. */
    val recurring: ImmutableList<RecurringCandidate> = persistentListOf(),
    val trackedPayees: ImmutableSet<String> = persistentSetOf(),
    val exporting: Boolean = false,
) {
    val spentDelta: Double
        get() = currentSpent - previousSpent

    val saved: Double
        get() = currentIncome - currentSpent
}

sealed interface InsightsEvent {
    data object Refresh : InsightsEvent
    data class TrackRecurring(val candidate: RecurringCandidate) : InsightsEvent
    data class ExportPdf(val uri: Uri) : InsightsEvent
}
