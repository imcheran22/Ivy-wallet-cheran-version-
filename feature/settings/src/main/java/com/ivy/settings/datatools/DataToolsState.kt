package com.ivy.settings.datatools

import androidx.compose.runtime.Immutable
import com.ivy.domain.usecase.datatools.ArchivableAccount
import com.ivy.domain.usecase.datatools.BulkTransactionRow
import com.ivy.domain.usecase.datatools.DuplicateGroup
import com.ivy.domain.usecase.quickadd.QuickAddCategoryOption
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import java.util.UUID

enum class DataToolsTab {
    DUPLICATES, RECATEGORIZE, ACCOUNTS, BACKUPS
}

@Immutable
data class DataToolsState(
    val tab: DataToolsTab = DataToolsTab.DUPLICATES,
    val loading: Boolean = true,
    val duplicates: ImmutableList<DuplicateGroup> = persistentListOf(),
    val rows: ImmutableList<BulkTransactionRow> = persistentListOf(),
    val selectedIds: ImmutableSet<UUID> = persistentSetOf(),
    val categories: ImmutableList<QuickAddCategoryOption> = persistentListOf(),
    val onlyUncategorized: Boolean = true,
    val query: String = "",
    val accounts: ImmutableList<ArchivableAccount> = persistentListOf(),
    val message: DataToolsMessage? = null,
    val autoBackupEnabled: Boolean = false,
    val lastBackupEpochMs: Long? = null,
    val lastBackupResult: String? = null,
)

/** Something that just happened, said in words the screen can render. */
sealed interface DataToolsMessage {
    data class Merged(val removed: Int) : DataToolsMessage
    data class Recategorized(val count: Int) : DataToolsMessage
}

sealed interface DataToolsEvent {
    data class SelectTab(val tab: DataToolsTab) : DataToolsEvent
    data class Merge(val keepId: UUID, val allIds: List<UUID>) : DataToolsEvent
    data class ToggleSelected(val id: UUID) : DataToolsEvent
    data object SelectAll : DataToolsEvent
    data object ClearSelection : DataToolsEvent
    data class SetQuery(val query: String) : DataToolsEvent
    data class SetOnlyUncategorized(val enabled: Boolean) : DataToolsEvent
    data class ApplyCategory(val categoryId: UUID?) : DataToolsEvent
    data class SetArchived(val accountId: UUID, val archived: Boolean) : DataToolsEvent
    data object DismissMessage : DataToolsEvent
    data class SetAutoBackup(val enabled: Boolean) : DataToolsEvent
    data object BackUpNow : DataToolsEvent
}
