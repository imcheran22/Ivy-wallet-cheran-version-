package com.ivy.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.domain.usecase.insights.NetWorthTrendUseCase
import android.net.Uri
import com.ivy.domain.usecase.insights.SpendingComparisonUseCase
import com.ivy.domain.usecase.recurring.RecurringCandidate
import com.ivy.domain.usecase.recurring.RecurringPaymentUseCase
import com.ivy.domain.usecase.statement.PdfStatementUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val spendingComparisonUseCase: SpendingComparisonUseCase,
    private val netWorthTrendUseCase: NetWorthTrendUseCase,
    private val recurringPaymentUseCase: RecurringPaymentUseCase,
    private val pdfStatementUseCase: PdfStatementUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(InsightsState())
    val state: StateFlow<InsightsState> = _state.asStateFlow()

    /**
     * Emitted once the PDF is actually on disk. Sharing the file the moment the export starts
     * would hand the other app an empty document.
     */
    private val _pdfReady = MutableSharedFlow<Uri>(extraBufferCapacity = 1)
    val pdfReady: SharedFlow<Uri> = _pdfReady.asSharedFlow()

    init {
        onEvent(InsightsEvent.Refresh)
    }

    fun onEvent(event: InsightsEvent) {
        when (event) {
            InsightsEvent.Refresh -> refresh()
            is InsightsEvent.TrackRecurring -> track(event.candidate)
            is InsightsEvent.ExportPdf -> exportPdf(event.uri)
        }
    }

    private fun track(candidate: RecurringCandidate) {
        viewModelScope.launch {
            if (recurringPaymentUseCase.trackAsPlannedPayment(candidate)) {
                _state.value = _state.value.copy(
                    trackedPayees = (_state.value.trackedPayees + candidate.payee)
                        .toImmutableSet(),
                )
            }
        }
    }

    private fun exportPdf(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(exporting = true)
            pdfStatementUseCase.exportCurrentMonth(uri).onSuccess { _pdfReady.tryEmit(uri) }
            _state.value = _state.value.copy(exporting = false)
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            val comparison = runCatching { spendingComparisonUseCase.load() }.getOrNull()
            val trend = runCatching { netWorthTrendUseCase.load() }.getOrNull()
            val recurring = runCatching { recurringPaymentUseCase.detect() }
                .getOrDefault(emptyList())

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
                recurring = recurring.toImmutableList(),
            )
        }
    }
}
