package com.ivy.wallet.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ivy.notifmirror.data.MirrorPrefs
import com.ivy.notifmirror.service.MirrorNotificationHandler
import com.ivy.notifmirror.sync.PartnerTransactionHandler

class IvyFcmService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data

        when (data["feature"]) {
            "notif_mirror" -> MirrorNotificationHandler.handleIncomingMessage(
                applicationContext, data
            )
            "couple_transaction_sync" -> PartnerTransactionHandler.handleIncomingTransaction(
                applicationContext, data
            )
        }
    }

    override fun onNewToken(token: String) {
        val prefs = MirrorPrefs(applicationContext)
        prefs.topicId?.let { topicId ->
            com.google.firebase.messaging.FirebaseMessaging.getInstance()
                .subscribeToTopic(topicId)
        }
    }
}
