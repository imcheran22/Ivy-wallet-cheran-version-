package com.ivy.wallet.quickadd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.base.legacy.Theme
import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.read.SettingsDao
import com.ivy.domain.usecase.quickadd.QuickAddOptionsUseCase
import com.ivy.domain.usecase.quickadd.QuickAddPresetStore
import com.ivy.domain.usecase.quickadd.QuickAddTransactionUseCase
import com.ivy.legacy.IvyWalletCtx
import com.ivy.legacy.utils.amountToDoubleOrNull
import com.ivy.legacy.utils.format
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Drives the floating quick-add sheet.
 *
 * The sheet is opened from outside the app - a widget, the Quick Settings tile, a notification -
 * so it owns everything it needs to stand alone, down to picking up the user's theme.
 */
@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val quickAdd: QuickAddTransactionUseCase,
    private val optionsUseCase: QuickAddOptionsUseCase,
    private val presetStore: QuickAddPresetStore,
    private val settingsDao: SettingsDao,
    private val ivyContext: IvyWalletCtx,
) : ViewModel() {

    private val _state = MutableStateFlow(QuickAddUiState())
    val state: StateFlow<QuickAddUiState> = _state.asStateFlow()

    /** Emitted when the sheet has nothing left to do and the activity should close. */
    private val _dismiss = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val dismiss: SharedFlow<Unit> = _dismiss.asSharedFlow()

    private var started = false

    fun start(type: TransactionType, presetId: UUID?) {
        if (started) return
        started = true

        viewModelScope.launch {
            applyUserTheme()

            val options = optionsUseCase.load()
            val presets = presetStore.all()
            val account = options.defaultAccount

            _state.update {
                it.copy(
                    loading = false,
                    type = type,
                    accounts = options.accounts.toImmutableList(),
                    categories = options.categories.toImmutableList(),
                    presets = presets.toImmutableList(),
                    selectedAccountId = account?.id,
                    currency = account?.assetCode.orEmpty(),
                    noAccounts = options.accounts.isEmpty(),
                )
            }

            // Opened by tapping a preset on a widget: there is nothing left to ask, so file it.
            presetId?.let { onEvent(QuickAddEvent.ApplyPreset(it)) }
        }
    }

    fun onEvent(event: QuickAddEvent) {
        when (event) {
            is QuickAddEvent.SwitchType -> _state.update { it.copy(type = event.type) }
            is QuickAddEvent.SetAmount -> _state.update { it.copy(amount = event.amount) }
            is QuickAddEvent.SelectCategory -> _state.update {
                it.copy(selectedCategoryId = event.categoryId)
            }

            is QuickAddEvent.SelectAccount -> selectAccount(event.accountId)
            is QuickAddEvent.ApplyPreset -> applyPreset(event.presetId)
            QuickAddEvent.Save -> save()
            QuickAddEvent.Undo -> undo()
        }
    }

    private fun selectAccount(accountId: UUID) {
        _state.update { state ->
            val account = state.accounts.firstOrNull { it.id == accountId }
            state.copy(
                selectedAccountId = accountId,
                currency = account?.assetCode ?: state.currency,
            )
        }
    }

    private fun applyPreset(presetId: UUID) {
        viewModelScope.launch {
            val preset = presetStore.findById(presetId) ?: return@launch
            add(
                type = preset.type,
                amount = preset.amount,
                accountId = preset.accountId ?: _state.value.selectedAccountId,
                categoryId = preset.categoryId,
                title = preset.label,
            )
        }
    }

    private fun save() {
        val state = _state.value
        val amount = state.amount.amountToDoubleOrNull() ?: return
        if (amount <= 0.0) return

        viewModelScope.launch {
            add(
                type = state.type,
                amount = amount,
                accountId = state.selectedAccountId,
                categoryId = state.selectedCategoryId,
                title = null,
            )
        }
    }

    @Suppress("LongParameterList")
    private suspend fun add(
        type: TransactionType,
        amount: Double,
        accountId: UUID?,
        categoryId: UUID?,
        title: String?,
    ) {
        when (
            val result = quickAdd.add(
                type = type,
                amount = amount,
                accountId = accountId,
                categoryId = categoryId,
                title = title,
            )
        ) {
            is QuickAddTransactionUseCase.Result.Added -> _state.update {
                it.copy(
                    saved = QuickAddSaved(
                        transactionId = result.transactionId,
                        amountText = "${result.amount.format(result.assetCode)} ${result.assetCode}",
                        categoryName = result.categoryName,
                        accountName = result.accountName,
                        type = result.type,
                    )
                )
            }

            QuickAddTransactionUseCase.Result.NoAccounts ->
                _state.update { it.copy(noAccounts = true) }

            QuickAddTransactionUseCase.Result.InvalidAmount -> Unit
        }
    }

    private fun undo() {
        val saved = _state.value.saved ?: return
        viewModelScope.launch {
            quickAdd.undo(saved.transactionId)
            _dismiss.tryEmit(Unit)
        }
    }

    /**
     * The sheet can be the first thing this process draws, so the theme has to be read here
     * rather than inherited from a running app.
     */
    private suspend fun applyUserTheme() {
        val theme = runCatching { settingsDao.findAll().firstOrNull()?.theme }.getOrNull()
        ivyContext.switchTheme(theme ?: Theme.AUTO)
    }
}
