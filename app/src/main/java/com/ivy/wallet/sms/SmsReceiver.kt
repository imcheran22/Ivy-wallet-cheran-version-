package com.ivy.wallet.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Listens for incoming SMS and hands each message off to [SmsImportWorker] for parsing.
 *
 * Deliberately does no filtering/parsing here beyond grouping multi-part SMS back into one
 * body - the worker checks whether SMS auto-import is actually enabled and whether the message
 * looks like a bank transaction, so this receiver stays fast and can't violate onReceive's
 * execution time limit.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        val receivedAtEpochMillis = messages.first().timestampMillis

        val request = OneTimeWorkRequestBuilder<SmsImportWorker>()
            .setInputData(
                Data.Builder()
                    .putString(SmsImportWorker.INPUT_SMS_BODY, body)
                    .putLong(SmsImportWorker.INPUT_RECEIVED_AT_EPOCH_MILLIS, receivedAtEpochMillis)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }
}
