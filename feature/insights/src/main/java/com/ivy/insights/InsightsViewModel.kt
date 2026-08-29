package com.ivy.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.domain.usecase.insights.NetWorthTrendUseCase
import com.ivy.domain.usecase.insights.SpendingComparisonUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val spendingComparisonUseCase: SpendingComparisonUseCase,
    private val netWorthTrendUseCase: NetWorthTrendUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(InsightsState())
    val state: StateFlow<InsightsState> = _state.asStateFlow()

    init {
        onEvent(InsightsEvent.Refresh)
    }

    fun onEvent(event: InsightsEvent) {
        when (event) {
            InsightsEvent.Refresh -> refresh()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            val comparison = runCatching { spendingComparisonUseCase.load() }.getOrNull()
            val trend = runCatching { netWorthTrendUseCase.load() }.getOrNull()

            _state.value = InsightsState(
                loading = false,
                currency = comparison?.currency ?: trend?.currency.orEmpty(),
                periodLabel = comparison?.period?.label().orEmpty(),
                currentSpent = comparison?.currentSpent ?: 0.0,
                previousSpent = comparison?.previousSpent ?: 0.0,
                currentIncome = comparison?.currentIncome ?: 0.0,
                previousIncome = comparison?.previousIncome ?: 0.0,
                categories = comparison?.categories.orEmpty().toImmutableList(),
                topPayees = comparison?.topPayees.orEmpty().toImmutableList(),
                netWorthPoints = trend?.points.orEmpty().toImmutableList(),
                netWorthNow = trend?.current?.netWorth ?: 0.0,
                netWorthChange = trend?.change ?: 0.0,
            )
        }
    }
}
