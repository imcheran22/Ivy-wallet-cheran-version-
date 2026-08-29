package com.ivy.wallet.quickadd

import androidx.compose.runtime.Immutable
import com.ivy.base.model.TransactionType
import com.ivy.domain.usecase.quickadd.QuickAddAccountOption
import com.ivy.domain.usecase.quickadd.QuickAddCategoryOption
import com.ivy.domain.usecase.quickadd.QuickAddPreset
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.util.UUID

@Immutable
data class QuickAddUiState(
    val loading: Boolean = true,
    val type: TransactionType = TransactionType.EXPENSE,
    val amount: String = "",
    val accounts: ImmutableList<QuickAddAccountOption> = persistentListOf(),
    val categories: ImmutableList<QuickAddCategoryOption> = persistentListOf(),
    val presets: ImmutableList<QuickAddPreset> = persistentListOf(),
    val selectedAccountId: UUID? = null,
    val selectedCategoryId: UUID? = null,
    val currency: String = "",
    val saved: QuickAddSaved? = null,
    val noAccounts: Boolean = false,
) {
    val canSave: Boolean
        get() = !loading && !noAccounts && saved == null
}

/**
 * What was just written, kept around only long enough for the user to take it back.
 */
@Immutable
data class QuickAddSaved(
    val transactionId: UUID,
    val amountText: String,
    val categoryName: String?,
    val accountName: String,
    val type: TransactionType,
)

sealed interface QuickAddEvent {
    data class SwitchType(val type: TransactionType) : QuickAddEvent
    data class SetAmount(val amount: String) : QuickAddEvent
    data class SelectCategory(val categoryId: UUID?) : QuickAddEvent
    data class SelectAccount(val accountId: UUID) : QuickAddEvent
    data class ApplyPreset(val presetId: UUID) : QuickAddEvent
    data object Save : QuickAddEvent
    data object Undo : QuickAddEvent
}
