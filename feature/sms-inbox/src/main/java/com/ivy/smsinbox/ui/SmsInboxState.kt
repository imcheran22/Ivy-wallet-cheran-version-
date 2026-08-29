package com.ivy.smsinbox.ui

import androidx.compose.runtime.Immutable
import com.ivy.data.model.CategoryId
import com.ivy.data.model.TransactionId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class CategoryOption(
    val id: CategoryId,
    val name: String,
    val color: Int,
)

@Immutable
data class SmsInboxCard(
    val transactionId: TransactionId,
    /** Null payees show as a placeholder rather than an empty card. */
    val payee: String?,
    val amountLabel: String,
    /**
     * "Friday, 11:42 PM". A name can't jog your memory when it belongs to a stranger, but the
     * exact day and time usually can.
     */
    val whenLabel: String,
    val isIncome: Boolean,
    val timesInQueue: Int,
    val suggestedCategoryId: CategoryId?,
    val suggestionReason: String?,
    /** The bank's own words, shown when the parsed name is not enough to decide from. */
    val originalSms: String?,
)

@Immutable
data class SmsInboxUiState(
    val loading: Boolean = true,
    val cards: ImmutableList<SmsInboxCard> = persistentListOf(),
    val categories: ImmutableList<CategoryOption> = persistentListOf(),
    val unsortedExpenseLabel: String = "",
    val unsortedIncomeLabel: String = "",
    val sortedThisSession: Int = 0,
    val skippedCount: Int = 0,
    /** True while any of the auto-capture categories don't exist yet. */
    val missingDefaultCategories: Boolean = false,
)

sealed interface SmsInboxEvent {
    data object Refresh : SmsInboxEvent

    /**
     * Right: sort it. [rememberPayee] is what makes the work shrink - it files every other
     * queued transaction with the same payee and every future one too.
     */
    data class Save(
        val transactionId: TransactionId,
        val categoryId: CategoryId,
        val rememberPayee: Boolean,
    ) : SmsInboxEvent

    /** Left: file it for later. One direction, one outcome - never two results per gesture. */
    data class Skip(val transactionId: TransactionId) : SmsInboxEvent

    data object RevisitSkipped : SmsInboxEvent

    data object CreateDefaultCategories : SmsInboxEvent
}
