package com.ivy.smsinbox.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.base.model.TransactionType
import com.ivy.data.model.TransactionId
import com.ivy.data.repository.CategoryRepository
import com.ivy.domain.usecase.sms.SmsCategories
import com.ivy.domain.usecase.sms.SmsInboxItem
import com.ivy.domain.usecase.sms.SmsSortingQueueUseCase
import com.ivy.ui.FormatMoneyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class SmsInboxViewModel @Inject constructor(
    private val sortingQueue: SmsSortingQueueUseCase,
    private val categoryRepository: CategoryRepository,
    private val formatMoneyUseCase: FormatMoneyUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SmsInboxUiState())
    val state: StateFlow<SmsInboxUiState> = _state.asStateFlow()

    /**
     * Skips live for the length of the session only. Skipping means "not right now", not
     * "never" - the user can pull them back with [SmsInboxEvent.RevisitSkipped], and they
     * return to the queue on the next visit regardless.
     */
    private var skipped: Set<TransactionId> = emptySet()
    private var sortedThisSession: Int = 0

    init {
        refresh()
    }

    fun onEvent(event: SmsInboxEvent) {
        when (event) {
            SmsInboxEvent.Refresh -> refresh()
            is SmsInboxEvent.Save -> save(event)
            is SmsInboxEvent.Skip -> {
                skipped = skipped + event.transactionId
                refresh()
            }

            SmsInboxEvent.RevisitSkipped -> {
                skipped = emptySet()
                refresh()
            }

            SmsInboxEvent.CreateDefaultCategories -> viewModelScope.launch {
                sortingQueue.createMissingDefaultCategories()
                refresh()
            }
        }
    }

    private fun save(event: SmsInboxEvent.Save) {
        viewModelScope.launch {
            val updated = sortingQueue.categorize(
                transactionId = event.transactionId,
                categoryId = event.categoryId,
                rememberPayee = event.rememberPayee,
            )
            sortedThisSession += updated
            refresh()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            val queue = sortingQueue.load()
            val categories = categoryRepository.findAll()
            val categoryNames = categories.map { it.name.value.lowercase() }.toSet()

            // Skipped cards go to the back of the queue rather than disappearing.
            val ordered = queue.items.sortedBy { it.transactionId in skipped }
            val assetCode = queue.assetCode.orEmpty()

            _state.value = SmsInboxUiState(
                loading = false,
                cards = ordered.map { it.toCard() }.toImmutableList(),
                categories = categories.map {
                    CategoryOption(id = it.id, name = it.name.value, color = it.color.value)
                }.toImmutableList(),
                unsortedExpenseLabel = moneyLabel(queue.unsortedExpense, assetCode),
                unsortedIncomeLabel = moneyLabel(queue.unsortedIncome, assetCode),
                sortedThisSession = sortedThisSession,
                skippedCount = queue.items.count { it.transactionId in skipped },
                missingDefaultCategories = SmsCategories.autoCaptureDefaults.any {
                    it.lowercase() !in categoryNames
                },
            )
        }
    }

    private suspend fun SmsInboxItem.toCard() = SmsInboxCard(
        transactionId = transactionId,
        payee = payee,
        amountLabel = moneyLabel(amount, assetCode),
        whenLabel = whenLabel(time),
        isIncome = type == TransactionType.INCOME,
        timesInQueue = timesInQueue,
        suggestedCategoryId = suggestedCategoryId,
        suggestionReason = suggestionReason,
    )

    private suspend fun moneyLabel(amount: Double, assetCode: String): String {
        val formatted = formatMoneyUseCase.format(value = amount, shortenAmount = false)
        return if (assetCode.isBlank()) formatted else "$formatted $assetCode"
    }

    private fun whenLabel(time: Instant): String {
        val zoned = time.atZone(ZoneId.systemDefault())
        val formatter = if (Duration.between(time, Instant.now()) < RECENT_WINDOW) {
            recentFormatter
        } else {
            olderFormatter
        }
        return formatter.format(zoned)
    }

    companion object {
        private val RECENT_WINDOW: Duration = Duration.ofDays(7)
        private val recentFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, h:mm a")
        private val olderFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a")
    }
}
