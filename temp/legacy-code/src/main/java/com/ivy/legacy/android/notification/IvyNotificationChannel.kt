package com.ivy.wallet.android.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.ContextCompat
import com.ivy.ui.R

enum class IvyNotificationChannel(
    val channelId: String,
    val channelName: String,
    val description: String,
    val importance: Int = NotificationManager.IMPORTANCE_MAX,
    val bypassDnd: Boolean = true
) {
    TRANSACTION_REMINDER(
        channelId = "transaction_reminder",
        channelName = "Transaction reminder",
        description = "Reminding you to record your transactions on a daily basis.",
        importance = NotificationManager.IMPORTANCE_HIGH,
        bypassDnd = false
    ),

    /**
     * The always-there quick-add notification. Deliberately the lowest importance there is:
     * it's a button the user chose to keep in their shade, not something with news.
     */
    QUICK_ADD(
        channelId = "quick_add",
        channelName = "Quick add",
        description = "A silent notification for logging a transaction without unlocking.",
        importance = NotificationManager.IMPORTANCE_MIN,
        bypassDnd = false
    ),

    DAILY_SUMMARY(
        channelId = "daily_summary",
        channelName = "Evening summary",
        description = "A nightly recap of what you spent and what still needs a category.",
        importance = NotificationManager.IMPORTANCE_DEFAULT,
        bypassDnd = false
    );

    @SuppressLint("WrongConstant")
    fun create(context: Context): NotificationChannel {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        val colorPurple = ContextCompat.getColor(context, R.color.green)
        val channel = NotificationChannel(
            channelId,
            channelName,
            importance
        )
        channel.description = description
        channel.lightColor = colorPurple
        // A low-importance channel that still blinks and buzzes isn't low-importance.
        val noisy = importance >= NotificationManager.IMPORTANCE_DEFAULT
        channel.enableLights(noisy)
        channel.enableVibration(noisy)
        channel.setBypassDnd(false)
        return channel
    }
}
