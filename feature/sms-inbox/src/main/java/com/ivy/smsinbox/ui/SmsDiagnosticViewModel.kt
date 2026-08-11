package com.ivy.smsinbox.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.base.model.TransactionType
import com.ivy.domain.usecase.sms.BankSmsParser
import com.ivy.domain.usecase.sms.ImportSmsTransactionUseCase
import com.ivy.domain.usecase.sms.SmsCategoryGuesser
import com.ivy.smsinbox.data.DeviceSms
import com.ivy.smsinbox.data.DeviceSmsReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@Immutable
data class SenderSummary(
    val sender: String,
    val moneyLike: Int,
    val parsed: Int,
)

@Immutable
data class DiagnosticRow(
    val sender: String,
    val receivedLabel: String,
    val parsed: Boolean,
    val amountLabel: String?,
    val isIncome: Boolean,
    val payee: String?,
    val guessLabel: String?,
    val preview: String,
)

@Immutable
data class SmsDiagnosticUiState(
    val loading: Boolean = true,
    val permissionGranted: Boolean = false,
    val scannedCount: Int = 0,
    val moneyLikeCount: Int = 0,
    val senders: ImmutableList<SenderSummary> = persistentListOf(),
    val rows: ImmutableList<DiagnosticRow> = persistentListOf(),
    val importing: Boolean = false,
    val importResult: String? = null,
)

sealed interface SmsDiagnosticEvent {
    data object Scan : SmsDiagnosticEvent
    data object ImportAll : SmsDiagnosticEvent
}

/**
 * The dry run. Reads messages already on the device and shows exactly what the rules would
 * extract from each one - without writing anything to the wallet.
 *
 * This is the step worth doing before trusting any of it: two of the worst parsing bugs in
 * this feature came from assumptions that a thirty-second look at real messages would have
 * killed. Only once the amounts on this screen match the messages should anything be imported.
 */
@HiltViewModel
class SmsDiagnosticViewModel @Inject constructor(
    private val reader: DeviceSmsReader,
    private val importSmsTransactionUseCase: ImportSmsTransactionUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SmsDiagnosticUiState())
    val state: StateFlow<SmsDiagnosticUiState> = _state.asStateFlow()

    init {
        scan()
    }

    fun onEvent(event: SmsDiagnosticEvent) {
        when (event) {
            SmsDiagnosticEvent.Scan -> scan()
            SmsDiagnosticEvent.ImportAll -> importAll()
        }
    }

    private fun scan() {
        viewModelScope.launch {
            if (!reader.hasPermission()) {
                _state.value = SmsDiagnosticUiState(loading = false, permissionGranted = false)
                return@launch
            }
            val messages = reader.readRecent()
            val moneyLike = messages.filter { BankSmsParser.looksLikeMoneyAlert(it.body) }

            _state.value = SmsDiagnosticUiState(
                loading = false,
                permissionGranted = true,
                scannedCount = messages.size,
                moneyLikeCount = moneyLike.size,
                senders = summarise(moneyLike),
                rows = moneyLike.map { it.toRow() }.toImmutableList(),
            )
        }
    }

    /**
     * Backfills everything the parser understood. Safe to run more than once: each alert
     * carries a dedupe key, so messages already captured live are skipped rather than doubled.
     */
    private fun importAll() {
        viewModelScope.launch {
            _state.value = _state.value.copy(importing = true, importResult = null)
            val messages = reader.readRecent().filter { BankSmsParser.looksLikeMoneyAlert(it.body) }
            var imported = 0
            var skipped = 0
            messages.reversed().forEach { sms ->
                val result = importSmsTransactionUseCase.import(
                    smsBody = sms.body,
                    receivedAt = sms.receivedAt,
                )
                when (result) {
                    is ImportSmsTransactionUseCase.Result.Imported -> imported++
                    else -> skipped++
                }
            }
            _state.value = _state.value.copy(
                importing = false,
                importResult = "Imported $imported, skipped $skipped " +
                    "(already imported or not a transaction).",
            )
            scan()
        }
    }

    private fun summarise(messages: List<DeviceSms>): ImmutableList<SenderSummary> = messages
        .groupBy { it.sender }
        .map { (sender, group) ->
            SenderSummary(
                sender = sender,
                moneyLike = group.size,
                parsed = group.count { BankSmsParser.parse(it.body) != null },
            )
        }
        .sortedByDescending { it.moneyLike }
        .toImmutableList()

    private fun DeviceSms.toRow(): DiagnosticRow {
        val parsed = BankSmsParser.parse(body)
        val guess = parsed?.let { SmsCategoryGuesser.guess(it) }
        return DiagnosticRow(
            sender = sender,
            receivedLabel = formatter.format(receivedAt.atZone(ZoneId.systemDefault())),
            parsed = parsed != null,
            amountLabel = parsed?.amount?.let { "%.2f".format(it) },
            isIncome = parsed?.type == TransactionType.INCOME,
            payee = parsed?.payee,
            guessLabel = guess?.let { "${it.categoryName} - ${it.reason}" },
            preview = body.take(PREVIEW_LENGTH).replace(Regex("\\s+"), " "),
        )
    }

    companion object {
        private const val PREVIEW_LENGTH = 140
        private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, h:mm a")
    }
}
