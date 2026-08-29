package com.ivy.wallet.quickadd

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ivy.base.model.TransactionType
import com.ivy.domain.sync.CloudSyncTrigger
import com.ivy.domain.usecase.quickadd.QuickAddTransactionUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Files what was typed into the quick-add notification, then re-posts the notification saying
 * what happened.
 *
 * The reply consumes the notification's input box, so it has to be rebuilt either way. Putting
 * the outcome in its title is the only feedback available to someone who never unlocked the
 * phone - and a silent failure here would mean money quietly not recorded, which is worse than
 * no feature at all.
 */
@HiltWorker
class QuickAddWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val quickAddTransactionUseCase: QuickAddTransactionUseCase,
    private val quickAddNotification: QuickAddNotification,
    private val cloudSyncTrigger: CloudSyncTrigger,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val text = inputData.getString(INPUT_TEXT).orEmpty()

        when (val result = quickAddTransactionUseCase.add(text)) {
            is QuickAddTransactionUseCase.Result.Added -> {
                runCatching { cloudSyncTrigger.syncNow() }
                quickAddNotification.show(status = describe(result))
            }

            QuickAddTransactionUseCase.Result.NotUnderstood ->
                quickAddNotification.show(
                    status = "Couldn't read that - try \"250 coffee\""
                )

            QuickAddTransactionUseCase.Result.NoAccountsConfigured ->
                quickAddNotification.show(status = "No account to file it against")
        }

        Result.success()
    }

    private fun describe(result: QuickAddTransactionUseCase.Result.Added): String = buildString {
        append(if (result.type == TransactionType.INCOME) "Added +" else "Added ")
        append(result.amount.toBigDecimal().stripTrailingZeros().toPlainString())
        append(" ")
        append(result.currency)
        result.title?.let {
            append(" · ")
            append(it)
        }
    }

    companion object {
        const val INPUT_TEXT = "quick_add_text"
    }
}
