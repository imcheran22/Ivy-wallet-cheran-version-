package com.ivy.notifmirror.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ivy.notifmirror.R
import com.ivy.notifmirror.data.MirrorNotificationChannels
import com.ivy.notifmirror.data.MirrorPrefs

class MirrorForegroundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MirrorNotificationChannels.createChannels(applicationContext)

        val prefs = MirrorPrefs(applicationContext)
        val modeLabel = when (prefs.mode) {
            MirrorPrefs.MODE_SENDER -> "Sender"
            MirrorPrefs.MODE_RECEIVER -> "Receiver"
            else -> "Active"
        }

        startForeground(NOTIFICATION_ID, buildNotification(modeLabel))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(modeLabel: String): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            launchIntent ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, MirrorNotificationChannels.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_mirror_notification)
            .setContentTitle("Couple Mirror")
            .setContentText("Running in $modeLabel mode")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 9001

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, MirrorForegroundService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(
                Intent(context, MirrorForegroundService::class.java)
            )
        }
    }
}
