package com.notifmirror

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class NotifMirrorApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

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

    companion object {
        const val CHANNEL_SERVICE = "mirror_service"
        const val CHANNEL_MIRRORED = "mirrored_notifications"
    }
}
