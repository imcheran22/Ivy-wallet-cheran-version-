package com.ivy.battery.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ivy.battery.domain.model.BatterySnapshot
import com.ivy.battery.domain.model.TimeEstimates
import com.ivy.battery.ui.format.formatCurrent
import com.ivy.battery.ui.format.formatDuration
import com.ivy.battery.ui.format.formatTemperature
import com.ivy.battery.ui.format.formatVoltage
import com.ivy.battery.ui.format.formatWatts
import com.ivy.ui.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the battery feature's notification channels and the two notifications it
 * posts: the ongoing monitor and the alarms.
 */
@Singleton
class BatteryNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createChannels() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                "Battery monitor",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Ongoing notification with live battery statistics"
                setShowBadge(false)
                enableVibration(false)
            }
        )

        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALARMS,
                "Battery alarms",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Charge, low battery and overheating alarms"
                enableVibration(true)
            }
        )
    }

    fun statusNotification(
        snapshot: BatterySnapshot?,
        estimates: TimeEstimates?,
        detailed: Boolean,
        fahrenheit: Boolean,
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_vue_main_battery_charging)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (snapshot == null) {
            return builder
                .setContentTitle("Battery monitoring")
                .setContentText("Starting…")
                .build()
        }

        val stateLabel = if (snapshot.charging) {
            "Charging · ${snapshot.plugType.label}"
        } else {
            snapshot.status.label
        }

        if (!detailed) {
            return builder
                .setContentTitle("${snapshot.level}% · $stateLabel")
                .setContentText("Battery monitoring is active")
                .build()
        }

        val remaining = when {
            snapshot.charging -> estimates?.timeToFullMs
                ?.let { "${formatDuration(it)} to full" }

            else -> estimates?.mixedRemainingMs
                ?.let { "${formatDuration(it)} left" }
        }

        val details = listOfNotNull(
            formatCurrent(snapshot.currentMa),
            formatWatts(snapshot.powerWatts),
            formatTemperature(snapshot.temperatureC, fahrenheit),
            formatVoltage(snapshot.voltageMv),
            remaining,
        ).joinToString(" · ")

        return builder
            .setContentTitle("${snapshot.level}% · $stateLabel")
            .setContentText(details)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setSubText(snapshot.technology)
            .build()
    }

    fun showAlarm(id: Int, title: String, message: String, withSound: Boolean) {
        val stopIntent = PendingIntent.getForegroundService(
            context,
            id,
            Intent(context, BatteryMonitorService::class.java)
                .setAction(BatteryMonitorService.ACTION_STOP_ALARM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALARMS)
            .setSmallIcon(R.drawable.ic_vue_main_battery_charging)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(if (withSound) NotificationCompat.DEFAULT_ALL else 0)
            .addAction(0, "Stop alarm", stopIntent)
            .build()

        runCatching { notificationManager.notify(id, notification) }
    }

    fun cancelAlarms() {
        runCatching {
            notificationManager.cancel(ALARM_ID_CHARGE)
            notificationManager.cancel(ALARM_ID_LOW)
            notificationManager.cancel(ALARM_ID_TEMPERATURE)
        }
    }

    fun updateStatus(notification: Notification) {
        runCatching { notificationManager.notify(STATUS_ID, notification) }
    }

    private fun openAppIntent(): PendingIntent? {
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_STATUS = "ivy_battery_status"
        const val CHANNEL_ALARMS = "ivy_battery_alarms"
        const val STATUS_ID = 8100
        const val ALARM_ID_CHARGE = 8101
        const val ALARM_ID_LOW = 8102
        const val ALARM_ID_TEMPERATURE = 8103
    }
}
