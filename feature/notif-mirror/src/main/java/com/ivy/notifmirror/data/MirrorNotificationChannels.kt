package com.ivy.notifmirror.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object MirrorNotificationChannels {
    const val CHANNEL_SERVICE = "mirror_service"
    const val CHANNEL_MIRRORED = "mirrored_notifications"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "Mirror Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent notification while the notification mirror is active"
        }

        val mirroredChannel = NotificationChannel(
            CHANNEL_MIRRORED,
            "Mirrored Notifications",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications mirrored from your other device"
        }

        manager.createNotificationChannel(serviceChannel)
        manager.createNotificationChannel(mirroredChannel)
    }
}
