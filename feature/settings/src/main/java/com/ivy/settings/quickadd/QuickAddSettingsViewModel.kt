package com.ivy.settings.quickadd

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.data.datastore.DatastoreKeys
import com.ivy.domain.NotificationController
import com.ivy.domain.WidgetRefresher
import com.ivy.domain.usecase.quickadd.QuickAddOptionsUseCase
import com.ivy.domain.usecase.quickadd.QuickAddPreset
import com.ivy.domain.usecase.quickadd.QuickAddPresetStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class QuickAddSettingsViewModel @Inject constructor(
    private val presetStore: QuickAddPresetStore,
    private val optionsUseCase: QuickAddOptionsUseCase,
    private val notificationController: NotificationController,
    private val widgetRefresher: WidgetRefresher,
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    private val _state = MutableStateFlow(QuickAddSettingsState())
    val state: StateFlow<QuickAddSettingsState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onEvent(event: QuickAddSettingsEvent) {
        when (event) {
            is QuickAddSettingsEvent.SetNotificationEnabled -> setNotification(event.enabled)
            is QuickAddSettingsEvent.SetDailySummaryEnabled -> setDailySummary(event.enabled)
            QuickAddSettingsEvent.AddPreset -> _state.update {
                it.copy(draft = PresetDraft(accountId = it.accounts.firstOrNull()?.id))
            }

            is QuickAddSettingsEvent.EditPreset -> editPreset(event.id)
            is QuickAddSettingsEvent.DeletePreset -> viewModelScope.launch {
                presetStore.delete(event.id)
                afterPresetChange()
            }

            is QuickAddSettingsEvent.Move -> move(event.id, event.up)
            is QuickAddSettingsEvent.UpdateDraft -> _state.update { it.copy(draft = event.draft) }
            QuickAddSettingsEvent.SaveDraft -> saveDraft()
            QuickAddSettingsEvent.DismissDraft -> _state.update { it.copy(draft = null) }
        }
    }

    private fun setNotification(enabled: Boolean) {
        _state.update { it.copy(notificationEnabled = enabled) }
        viewModelScope.launch {
            dataStore.edit { it[DatastoreKeys.QUICK_ADD_NOTIFICATION_ENABLED] = enabled }
            notificationController.refreshQuickAddNotification()
        }
    }

    private fun setDailySummary(enabled: Boolean) {
        _state.update { it.copy(dailySummaryEnabled = enabled) }
        viewModelScope.launch {
            dataStore.edit { it[DatastoreKeys.DAILY_SUMMARY_ENABLED] = enabled }
            if (enabled) {
                notificationController.scheduleDailySummary()
            } else {
                notificationController.cancelDailySummary()
            }
        }
    }

    private fun editPreset(id: UUID) {
        viewModelScope.launch {
            val preset = presetStore.findById(id) ?: return@launch
            _state.update {
                it.copy(
                    draft = PresetDraft(
                        id = preset.id,
                        label = preset.label,
                        amount = preset.amount.toString(),
                        type = preset.type,
                        categoryId = preset.categoryId,
                        accountId = preset.accountId,
                    )
                )
            }
        }
    }

    private fun move(id: UUID, up: Boolean) {
        viewModelScope.launch {
            val presets = presetStore.all().toMutableList()
            val index = presets.indexOfFirst { it.id == id }
            val target = if (up) index - 1 else index + 1
            if (index == -1 || target !in presets.indices) return@launch

            val moved = presets.removeAt(index)
            presets.add(target, moved)
            presetStore.replaceAll(
                presets.mapIndexed { position, preset ->
                    preset.copy(orderNum = position.toDouble())
                }
            )
            afterPresetChange()
        }
    }

    private fun saveDraft() {
        val draft = _state.value.draft ?: return
        val amount = draft.amount.toDoubleOrNull() ?: return
        if (!draft.isValid) return

        viewModelScope.launch {
            val existing = draft.id?.let { presetStore.findById(it) }
            presetStore.save(
                QuickAddPreset(
                    id = draft.id ?: UUID.randomUUID(),
                    label = draft.label.trim(),
                    amount = amount,
                    type = draft.type,
                    categoryId = draft.categoryId,
                    accountId = draft.accountId,
                    orderNum = existing?.orderNum ?: presetStore.all().size.toDouble(),
                )
            )
            _state.update { it.copy(draft = null) }
            afterPresetChange()
        }
    }

    /**
     * Presets are drawn in three places outside this screen, none of which observe anything -
     * so a change has to go and tell them.
     */
    private suspend fun afterPresetChange() {
        refreshNow()
        widgetRefresher.refreshAll()
        notificationController.refreshQuickAddNotification()
    }

    private fun refresh() {
        viewModelScope.launch { refreshNow() }
    }

    private suspend fun refreshNow() {
        val options = optionsUseCase.load()
        val presets = presetStore.all()
        val prefs = dataStore.data.first()
        val defaultCurrency = options.defaultAccount?.assetCode.orEmpty()

        _state.update { state ->
            state.copy(
                loading = false,
                accounts = options.accounts.toImmutableList(),
                categories = options.categories.toImmutableList(),
                presets = presets.map { preset ->
                    val account = options.accounts.firstOrNull { it.id == preset.accountId }
                    PresetRow(
                        id = preset.id,
                        label = preset.label,
                        amount = preset.amount,
                        type = preset.type,
                        categoryName = options.categories
                            .firstOrNull { it.id == preset.categoryId }?.name,
                        accountName = account?.name,
                        currency = account?.assetCode ?: defaultCurrency,
                    )
                }.toImmutableList(),
                notificationEnabled = prefs[DatastoreKeys.QUICK_ADD_NOTIFICATION_ENABLED] ?: false,
                dailySummaryEnabled = prefs[DatastoreKeys.DAILY_SUMMARY_ENABLED] ?: false,
                presetLimitReached = presets.size >= QuickAddPreset.MAX_PRESETS,
            )
        }
    }
}
