package com.ivy.wallet.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ivy.notifmirror.data.MirrorPrefs
import com.ivy.notifmirror.service.MirrorNotificationHandler

class IvyFcmService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data

        when (data["feature"]) {
            "notif_mirror" -> MirrorNotificationHandler.handleIncomingMessage(
                applicationContext, data
            )
            // Add branches for other features here, e.g.:
            // "other_feature" -> OtherFeatureHandler.handle(applicationContext, data)
        }
    }

    override fun onNewToken(token: String) {
        val prefs = MirrorPrefs(applicationContext)
        if (prefs.mode == MirrorPrefs.MODE_RECEIVER) {
            prefs.topicId?.let { topicId ->
                com.google.firebase.messaging.FirebaseMessaging.getInstance()
                    .subscribeToTopic(topicId)
            }
        }
    }
}
