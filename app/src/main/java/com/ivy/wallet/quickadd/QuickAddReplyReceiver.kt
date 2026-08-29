package com.ivy.wallet.quickadd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Receives what was typed into the quick-add notification.
 *
 * Does no work itself: a broadcast receiver has a few seconds before the system considers it
 * hung, and writing a transaction means touching the database and possibly the cloud sync.
 * The text goes straight to [QuickAddWorker], which is also what makes the add survive the
 * phone being locked again immediately afterwards.
 */
class QuickAddReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != QuickAddNotification.ACTION_QUICK_ADD) return

        val typed = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(QuickAddNotification.KEY_TEXT_REPLY)
            ?.toString()
            .orEmpty()

        val request = OneTimeWorkRequestBuilder<QuickAddWorker>()
            .setInputData(
                Data.Builder()
                    .putString(QuickAddWorker.INPUT_TEXT, typed)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }
}
