package com.notifmirror.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.notifmirror.NotifMirrorApp
import com.notifmirror.R
import com.notifmirror.ui.MainActivity
import com.notifmirror.util.MirrorPrefs

class NotifMirrorFcmService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val feature = data["feature"]

        when (feature) {
            "notif_mirror" -> handleMirroredNotification(data)
            else -> {
                // Route to other feature handlers here.
                // Example:
                // "other_feature" -> OtherFeatureHandler.handle(data)
            }
        }
    }

    override fun onNewToken(token: String) {
        val prefs = MirrorPrefs(applicationContext)
        if (prefs.mode == MirrorPrefs.MODE_RECEIVER && prefs.topicId != null) {
            com.google.firebase.messaging.FirebaseMessaging.getInstance()
                .subscribeToTopic(prefs.topicId!!)
        }
    }

    private fun handleMirroredNotification(data: Map<String, String>) {
        val prefs = MirrorPrefs(applicationContext)
        if (prefs.mode != MirrorPrefs.MODE_RECEIVER) return

        val sourceApp = data["source_app"] ?: "Unknown App"
        val title = data["title"] ?: ""
        val text = data["text"] ?: ""
        val timestamp = data["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()

        prefs.lastSyncTime = System.currentTimeMillis()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notificationId = (sourceApp.hashCode() + timestamp).toInt()

        val notification = NotificationCompat.Builder(this, NotifMirrorApp.CHANNEL_MIRRORED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$sourceApp: $title")
            .setContentText(text)
            .setSubText(sourceApp)
            .setGroup("mirror_$sourceApp")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setWhen(timestamp)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val summaryNotification = NotificationCompat.Builder(this, NotifMirrorApp.CHANNEL_MIRRORED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(sourceApp)
            .setGroup("mirror_$sourceApp")
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, notification)
        manager.notify(sourceApp.hashCode(), summaryNotification)
    }
}
