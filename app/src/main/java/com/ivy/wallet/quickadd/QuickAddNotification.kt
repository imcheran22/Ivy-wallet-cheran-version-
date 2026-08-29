package com.ivy.wallet.quickadd

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.ivy.domain.quickadd.QuickAddNotifier
import com.ivy.ui.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A permanent notification with a text box, so a spend can be recorded without unlocking.
 *
 * This is the shortest path there is from paying for something to it being in the ledger:
 * pull down, type "250 coffee", send. No activity is started, so no unlock is required, and
 * it works on every Android the app supports rather than depending on lock screen widgets
 * being available on the device.
 *
 * The notification is silent and ongoing on purpose - it is a control, not an alert, and it
 * should never make a sound or ask to be dismissed.
 */
@Singleton
class QuickAddNotification @Inject constructor(
    @ApplicationContext private val context: Context,
) : QuickAddNotifier {

    override fun show() = show(status = null)

    fun show(status: String?) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Quick add",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "A permanent notification for logging a spend without opening the app."
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        )

        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("e.g. 250 coffee")
            .build()

        val replyIntent = Intent(context, QuickAddReplyReceiver::class.java).apply {
            action = ACTION_QUICK_ADD
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

        val action = NotificationCompat.Action.Builder(
            R.drawable.ic_notification,
            "Add",
            replyPendingIntent,
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.green))
            .setContentTitle(status ?: "Log a spend")
            .setContentText("Type an amount and what it was for.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            // Visible on the lock screen, or typing into it there is impossible - which is
            // the one place this is most worth having.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(action)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    override fun hide() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        runCatching { manager.cancel(NOTIFICATION_ID) }
    }

    companion object {
        const val KEY_TEXT_REPLY = "quick_add_text"
        const val ACTION_QUICK_ADD = "ivy.wallet.intent.action.quick_add"
        const val NOTIFICATION_ID = 4711
        private const val CHANNEL_ID = "quick_add"
        private const val REQUEST_CODE = 4711
    }
}
