package com.ivy.wallet.quickadd.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ivy.base.model.TransactionType
import com.ivy.domain.usecase.quickadd.QuickAddPresetStore
import com.ivy.domain.usecase.quickadd.QuickAddTransactionUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.util.UUID
import javax.inject.Inject

/**
 * Handles taps on the quick-add notification's buttons.
 *
 * The point of this path is that it never opens anything: a preset tapped from the lock screen
 * writes the transaction and swaps the notification for a confirmation, and the phone stays
 * locked throughout.
 */
@AndroidEntryPoint
class QuickAddNotificationReceiver : BroadcastReceiver() {

    @Inject
    lateinit var quickAdd: QuickAddTransactionUseCase

    @Inject
    lateinit var presetStore: QuickAddPresetStore

    @Inject
    lateinit var notificationManager: QuickAddNotificationManager

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_PRESET -> handlePreset(intent)
                    ACTION_UNDO -> handleUndo(intent)
                    ACTION_REFRESH -> notificationManager.refresh()
                    else -> Unit
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handlePreset(intent: Intent) {
        val presetId = intent.getStringExtra(EXTRA_PRESET_ID)?.toUuidOrNull() ?: return
        val preset = presetStore.findById(presetId) ?: return

        val result = quickAdd.add(
            type = preset.type,
            amount = preset.amount,
            accountId = preset.accountId,
            categoryId = preset.categoryId,
            title = preset.label,
        )

        if (result is QuickAddTransactionUseCase.Result.Added) {
            notificationManager.showSaved(
                savedText = savedText(result),
                transactionId = result.transactionId,
            )
            // The confirmation is only useful while undo is still on offer.
            delay(UNDO_WINDOW_MILLIS)
            notificationManager.refresh()
        }
    }

    private suspend fun handleUndo(intent: Intent) {
        intent.getStringExtra(EXTRA_TRANSACTION_ID)?.toUuidOrNull()?.let { quickAdd.undo(it) }
        notificationManager.refresh()
    }

    private fun savedText(result: QuickAddTransactionUseCase.Result.Added): String = buildString {
        append(if (result.type == TransactionType.EXPENSE) "-" else "+")
        append(AMOUNT_FORMAT.format(result.amount))
        append(' ')
        append(result.assetCode)
        result.categoryName?.let {
            append(" · ")
            append(it)
        }
    }

    private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()

    companion object {
        const val ACTION_PRESET = "com.ivy.wallet.action.QUICK_ADD_PRESET"
        const val ACTION_UNDO = "com.ivy.wallet.action.QUICK_ADD_UNDO"
        const val ACTION_REFRESH = "com.ivy.wallet.action.QUICK_ADD_REFRESH"

        const val EXTRA_PRESET_ID = "preset_id"
        const val EXTRA_TRANSACTION_ID = "transaction_id"

        private const val UNDO_WINDOW_MILLIS = 6_000L
        private val AMOUNT_FORMAT = DecimalFormat("###,###.##")
    }
}
