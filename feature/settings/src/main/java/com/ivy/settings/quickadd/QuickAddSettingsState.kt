package com.ivy.settings.quickadd

import androidx.compose.runtime.Immutable
import com.ivy.base.model.TransactionType
import com.ivy.domain.usecase.quickadd.QuickAddAccountOption
import com.ivy.domain.usecase.quickadd.QuickAddCategoryOption
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.util.UUID

@Immutable
data class PresetRow(
    val id: UUID,
    val label: String,
    val amount: Double,
    val type: TransactionType,
    val categoryName: String?,
    val accountName: String?,
    val currency: String,
)

/**
 * The preset being written, kept as text so a half-typed amount doesn't have to be a valid
 * number yet.
 */
@Immutable
data class PresetDraft(
    val id: UUID? = null,
    val label: String = "",
    val amount: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val categoryId: UUID? = null,
    val accountId: UUID? = null,
) {
    val isValid: Boolean
        get() = label.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0.0
}

@Immutable
data class QuickAddSettingsState(
    val loading: Boolean = true,
    val presets: ImmutableList<PresetRow> = persistentListOf(),
    val accounts: ImmutableList<QuickAddAccountOption> = persistentListOf(),
    val categories: ImmutableList<QuickAddCategoryOption> = persistentListOf(),
    val notificationEnabled: Boolean = false,
    val dailySummaryEnabled: Boolean = false,
    val draft: PresetDraft? = null,
    val presetLimitReached: Boolean = false,
)

sealed interface QuickAddSettingsEvent {
    data class SetNotificationEnabled(val enabled: Boolean) : QuickAddSettingsEvent
    data class SetDailySummaryEnabled(val enabled: Boolean) : QuickAddSettingsEvent
    data object AddPreset : QuickAddSettingsEvent
    data class EditPreset(val id: UUID) : QuickAddSettingsEvent
    data class DeletePreset(val id: UUID) : QuickAddSettingsEvent
    data class Move(val id: UUID, val up: Boolean) : QuickAddSettingsEvent
    data class UpdateDraft(val draft: PresetDraft) : QuickAddSettingsEvent
    data object SaveDraft : QuickAddSettingsEvent
    data object DismissDraft : QuickAddSettingsEvent
}
