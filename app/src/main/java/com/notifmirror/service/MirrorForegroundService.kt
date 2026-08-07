package com.notifmirror.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.notifmirror.NotifMirrorApp
import com.notifmirror.R
import com.notifmirror.ui.MainActivity
import com.notifmirror.util.MirrorPrefs

class MirrorForegroundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotifMirrorApp.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Notification Mirror")
            .setContentText("Running in $modeLabel mode")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 9001
    }
}
