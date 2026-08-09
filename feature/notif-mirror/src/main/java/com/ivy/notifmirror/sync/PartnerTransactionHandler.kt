package com.ivy.notifmirror.sync

import android.content.Context
import android.provider.Settings

object PartnerTransactionHandler {

    fun handleIncomingTransaction(context: Context, data: Map<String, String>) {
        val senderDeviceId = data["device_id"] ?: return
        val localDeviceId = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ANDROID_ID
        )
        if (senderDeviceId == localDeviceId) return

        val tx = PartnerTransaction(
            type = data["type"] ?: "EXPENSE",
            amount = data["amount"]?.toDoubleOrNull() ?: 0.0,
            title = data["title"] ?: "",
            currency = data["currency"] ?: "",
            category = data["category"] ?: "",
            accountName = data["account_name"] ?: "",
            dateTime = data["date_time"]?.toLongOrNull() ?: System.currentTimeMillis(),
            receivedAt = System.currentTimeMillis(),
        )

        val store = PartnerTransactionStore(context)
        store.save(tx)
    }
}
