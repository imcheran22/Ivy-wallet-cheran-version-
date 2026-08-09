package com.ivy.notifmirror.ui

import com.ivy.notifmirror.sync.PartnerTransaction
import com.ivy.notifmirror.sync.PartnerTransactionStore
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class PartnerUiState(
    val transactions: List<PartnerTransaction> = emptyList(),
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val mainCurrency: String = "",
)

@HiltViewModel
class PartnerTransactionsViewModel @Inject constructor(
    private val store: PartnerTransactionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(PartnerUiState())
    val state: StateFlow<PartnerUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val txns = store.loadAll()
        val income = txns.filter { it.type == "INCOME" }.sumOf { it.amount }
        val expense = txns.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val currency = txns.firstOrNull()?.currency ?: ""
        _state.value = PartnerUiState(
            transactions = txns,
            totalIncome = income,
            totalExpense = expense,
            mainCurrency = currency,
        )
    }

    fun clearAll() {
        store.clear()
        _state.value = PartnerUiState()
    }
}
