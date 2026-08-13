package com.ivy.notifmirror.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ivy.notifmirror.R
import com.ivy.notifmirror.data.MirrorNotificationChannels
import com.ivy.notifmirror.data.MirrorPrefs

/**
 * Turns a forwarded notification into one on this phone.
 *
 * The hard part is not delivery, it is attribution. A mirrored alert lands in the same shade,
 * with the same icon, as everything this phone raised itself - so unless it says otherwise, it
 * reads as yours. Every notification here therefore leads with the phone it came from, keeps
 * the originating app as secondary detail, and groups under one summary so a busy partner
 * phone cannot bury the rest of the shade.
 */
object MirrorNotificationHandler {

    private const val GROUP_KEY = "com.ivy.notifmirror.MIRRORED"
    private const val SUMMARY_ID = 1

    fun handleIncomingMessage(context: Context, data: Map<String, String>) {
        val prefs = MirrorPrefs(context)
        if (prefs.mode != MirrorPrefs.MODE_RECEIVER) return

        MirrorNotificationChannels.createChannels(context)

        val sourceApp = data["source_app"]?.takeIf { it.isNotBlank() } ?: "Unknown app"
        val deviceLabel = data["device_label"]?.takeIf { it.isNotBlank() } ?: "Partner's phone"
        val title = data["title"].orEmpty()
        val text = data["text"].orEmpty()
        val bigText = data["big_text"]?.takeIf { it.isNotBlank() } ?: text
        val timestamp = data["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()

        prefs.lastSyncTime = System.currentTimeMillis()

        if (title.isEmpty() && text.isEmpty()) return

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // The heading answers "where did this happen"; the app name is the smaller line above
        // it. Putting the app first, as this used to, buries the only detail that is not
        // already obvious from the content.
        val heading = if (title.isNotEmpty()) "$deviceLabel · $title" else deviceLabel

        val notification = NotificationCompat.Builder(
            context,
            MirrorNotificationChannels.CHANNEL_MIRRORED
        )
            .setSmallIcon(R.drawable.ic_mirror_notification)
            .setContentTitle(heading)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText).setSummaryText(sourceApp))
            .setSubText(sourceApp)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setWhen(timestamp)
            .setShowWhen(true)
            .setGroup(GROUP_KEY)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), notification)
        manager.notify(SUMMARY_ID, summaryNotification(context, deviceLabel, pendingIntent))
    }

    /**
     * Android only collapses a group once a summary exists; without it every mirrored alert
     * stacks separately and a chatty partner phone pushes everything else out of the shade.
     */
    private fun summaryNotification(
        context: Context,
        deviceLabel: String,
        pendingIntent: PendingIntent,
    ) = NotificationCompat.Builder(context, MirrorNotificationChannels.CHANNEL_MIRRORED)
        .setSmallIcon(R.drawable.ic_mirror_notification)
        .setContentTitle("Mirrored from $deviceLabel")
        .setGroup(GROUP_KEY)
        .setGroupSummary(true)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()
}
