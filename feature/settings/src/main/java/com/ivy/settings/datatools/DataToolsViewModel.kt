package com.ivy.settings.datatools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.ivy.data.datastore.DatastoreKeys
import com.ivy.domain.BackupController
import com.ivy.domain.usecase.datatools.AccountArchiveUseCase
import com.ivy.domain.usecase.datatools.BulkEditUseCase
import com.ivy.domain.usecase.datatools.BulkFilter
import com.ivy.domain.usecase.datatools.DuplicateDetectionUseCase
import com.ivy.domain.usecase.quickadd.QuickAddOptionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DataToolsViewModel @Inject constructor(
    private val duplicateDetectionUseCase: DuplicateDetectionUseCase,
    private val bulkEditUseCase: BulkEditUseCase,
    private val accountArchiveUseCase: AccountArchiveUseCase,
    private val optionsUseCase: QuickAddOptionsUseCase,
    private val backupController: BackupController,
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    private val _state = MutableStateFlow(DataToolsState())
    val state: StateFlow<DataToolsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            loadCategories()
            loadDuplicates()
        }
    }

    fun onEvent(event: DataToolsEvent) {
        when (event) {
            is DataToolsEvent.SelectTab -> selectTab(event.tab)
            is DataToolsEvent.Merge -> merge(event.keepId, event.allIds)
            is DataToolsEvent.ToggleSelected -> _state.update { state ->
                val selected = state.selectedIds.toMutableSet()
                if (!selected.add(event.id)) selected.remove(event.id)
                state.copy(selectedIds = selected.toImmutableSet())
            }

            DataToolsEvent.SelectAll -> _state.update { state ->
                state.copy(selectedIds = state.rows.map { it.id }.toImmutableSet())
            }

            DataToolsEvent.ClearSelection -> _state.update {
                it.copy(selectedIds = persistentSetOf())
            }

            is DataToolsEvent.SetQuery -> {
                _state.update { it.copy(query = event.query) }
                viewModelScope.launch { loadRows() }
            }

            is DataToolsEvent.SetOnlyUncategorized -> {
                _state.update { it.copy(onlyUncategorized = event.enabled) }
                viewModelScope.launch { loadRows() }
            }

            is DataToolsEvent.ApplyCategory -> applyCategory(event.categoryId)
            is DataToolsEvent.SetArchived -> setArchived(event.accountId, event.archived)
            DataToolsEvent.DismissMessage -> _state.update { it.copy(message = null) }
            is DataToolsEvent.SetAutoBackup -> setAutoBackup(event.enabled)
            DataToolsEvent.BackUpNow -> backupController.backUpNow()
        }
    }

    private fun selectTab(tab: DataToolsTab) {
        _state.update { it.copy(tab = tab, loading = true, message = null) }
        viewModelScope.launch {
            when (tab) {
                DataToolsTab.DUPLICATES -> loadDuplicates()
                DataToolsTab.RECATEGORIZE -> loadRows()
                DataToolsTab.ACCOUNTS -> loadAccounts()
                DataToolsTab.BACKUPS -> loadBackupState()
            }
        }
    }

    private fun merge(keepId: UUID, allIds: List<UUID>) {
        viewModelScope.launch {
            val removed = allIds.filterNot { it == keepId }
            duplicateDetectionUseCase.merge(keepId = keepId, removeIds = removed)
            loadDuplicates()
            _state.update { it.copy(message = DataToolsMessage.Merged(removed.size)) }
        }
    }

    private fun applyCategory(categoryId: UUID?) {
        val ids = _state.value.selectedIds.toList()
        if (ids.isEmpty()) return

        viewModelScope.launch {
            val updated = bulkEditUseCase.recategorize(ids, categoryId)
            _state.update {
                it.copy(
                    selectedIds = persistentSetOf(),
                    message = DataToolsMessage.Recategorized(updated),
                )
            }
            loadRows()
        }
    }

    private fun setArchived(accountId: UUID, archived: Boolean) {
        viewModelScope.launch {
            accountArchiveUseCase.setArchived(accountId, archived)
            loadAccounts()
        }
    }

    private fun setAutoBackup(enabled: Boolean) {
        _state.update { it.copy(autoBackupEnabled = enabled) }
        viewModelScope.launch {
            dataStore.edit { it[DatastoreKeys.AUTO_BACKUP_ENABLED] = enabled }
            if (enabled) {
                backupController.schedule()
                // Take one straight away: an automatic backup that starts tomorrow is no
                // comfort to someone who just decided they wanted backups.
                backupController.backUpNow()
            } else {
                backupController.cancel()
            }
        }
    }

    private suspend fun loadBackupState() {
        val prefs = dataStore.data.first()
        _state.update {
            it.copy(
                loading = false,
                autoBackupEnabled = prefs[DatastoreKeys.AUTO_BACKUP_ENABLED] ?: false,
                lastBackupEpochMs = prefs[DatastoreKeys.AUTO_BACKUP_LAST_RUN_EPOCH_MS],
                lastBackupResult = prefs[DatastoreKeys.AUTO_BACKUP_LAST_RESULT],
            )
        }
    }

    private suspend fun loadCategories() {
        val options = runCatching { optionsUseCase.load() }.getOrNull() ?: return
        _state.update { it.copy(categories = options.categories.toImmutableList()) }
    }

    private suspend fun loadDuplicates() {
        val groups = runCatching { duplicateDetectionUseCase.find() }.getOrDefault(emptyList())
        _state.update { it.copy(loading = false, duplicates = groups.toImmutableList()) }
    }

    private suspend fun loadRows() {
        val state = _state.value
        val rows = runCatching {
            bulkEditUseCase.find(
                BulkFilter(
                    onlyUncategorized = state.onlyUncategorized,
                    query = state.query,
                )
            )
        }.getOrDefault(emptyList())

        _state.update { it.copy(loading = false, rows = rows.toImmutableList()) }
    }

    private suspend fun loadAccounts() {
        val accounts = runCatching { accountArchiveUseCase.accounts() }.getOrDefault(emptyList())
        _state.update { it.copy(loading = false, accounts = accounts.toImmutableList()) }
    }
}
