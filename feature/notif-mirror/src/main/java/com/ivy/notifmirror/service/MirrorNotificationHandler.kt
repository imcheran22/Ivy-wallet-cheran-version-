package com.ivy.notifmirror.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ivy.notifmirror.R
import com.ivy.notifmirror.data.MirrorNotificationChannels
import com.ivy.notifmirror.data.MirrorPrefs

object MirrorNotificationHandler {

    fun handleIncomingMessage(context: Context, data: Map<String, String>) {
        val prefs = MirrorPrefs(context)
        if (prefs.mode != MirrorPrefs.MODE_RECEIVER) return

        MirrorNotificationChannels.createChannels(context)

        val sourceApp = data["source_app"] ?: "Unknown App"
        val title = data["title"] ?: ""
        val text = data["text"] ?: ""
        val timestamp = data["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()

        prefs.lastSyncTime = System.currentTimeMillis()

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context, 0,
            launchIntent ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notificationId = (sourceApp.hashCode() + timestamp).toInt()

        val notification = NotificationCompat.Builder(
            context, MirrorNotificationChannels.CHANNEL_MIRRORED
        )
            .setSmallIcon(R.drawable.ic_mirror_notification)
            .setContentTitle("$sourceApp: $title")
            .setContentText(text)
            .setSubText(sourceApp)
            .setGroup("mirror_$sourceApp")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setWhen(timestamp)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val summaryNotification = NotificationCompat.Builder(
            context, MirrorNotificationChannels.CHANNEL_MIRRORED
        )
            .setSmallIcon(R.drawable.ic_mirror_notification)
            .setContentTitle(sourceApp)
            .setGroup("mirror_$sourceApp")
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, notification)
        manager.notify(sourceApp.hashCode(), summaryNotification)
    }
}
