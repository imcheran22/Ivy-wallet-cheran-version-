package com.ivy.wallet.quickadd.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.ivy.base.model.TransactionType
import com.ivy.data.datastore.DatastoreKeys
import com.ivy.domain.AppStarter
import com.ivy.domain.usecase.quickadd.QuickAddPreset
import com.ivy.domain.usecase.quickadd.QuickAddPresetStore
import com.ivy.ui.R
import com.ivy.wallet.android.notification.IvyNotificationChannel
import com.ivy.wallet.android.notification.NotificationService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.text.DecimalFormat
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An always-there row in the notification shade for logging a transaction.
 *
 * This is the lock-screen path: the shade is reachable without unlocking, so a preset tapped here
 * records the spend before the phone is even open. It is deliberately the lowest-importance
 * notification the system offers - a permanent button, never an interruption.
 */
@Singleton
class QuickAddNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationService: NotificationService,
    private val appStarter: AppStarter,
    private val presetStore: QuickAddPresetStore,
    private val dataStore: DataStore<Preferences>,
) {

    suspend fun enabled(): Boolean =
        dataStore.data.first()[DatastoreKeys.QUICK_ADD_NOTIFICATION_ENABLED] ?: false

    /** Puts the notification in the state the user's settings say it should be in. */
    suspend fun refresh() {
        if (enabled()) show() else hide()
    }

    suspend fun show() {
        val presets = runCatching { presetStore.all() }.getOrDefault(emptyList())

        val builder = baseNotification()
            .setContentTitle(context.getString(R.string.quick_add))
            .setContentText(subtitle(presets))
            .addAction(
                0,
                context.getString(R.string.income),
                sheetIntent(TransactionType.INCOME, REQUEST_INCOME),
            )

        presets.take(MAX_PRESET_ACTIONS).forEachIndexed { index, preset ->
            builder.addAction(
                0,
                presetActionLabel(preset),
                presetIntent(preset.id, REQUEST_PRESET_BASE + index),
            )
        }

        notificationService.showNotification(builder, NOTIFICATION_ID)
    }

    /**
     * Replaces the buttons with what just happened and a way to take it back. Restored to the
     * normal notification by [QuickAddNotificationReceiver] once the undo window closes.
     */
    fun showSaved(savedText: String, transactionId: UUID) {
        val builder = baseNotification()
            .setContentTitle(context.getString(R.string.saved_amount, savedText))
            .setContentText(context.getString(R.string.quick_add))
            .addAction(0, context.getString(R.string.undo), undoIntent(transactionId))

        notificationService.showNotification(builder, NOTIFICATION_ID)
    }

    fun hide() {
        notificationService.dismissNotification(NOTIFICATION_ID)
    }

    private fun baseNotification() = notificationService
        .defaultIvyNotification(
            channel = IvyNotificationChannel.QUICK_ADD,
            autoCancel = false,
            priority = NotificationCompat.PRIORITY_MIN,
        )
        .setOngoing(true)
        .setSilent(true)
        .setShowWhen(false)
        .setOnlyAlertOnce(true)
        .setContentIntent(sheetIntent(TransactionType.EXPENSE, REQUEST_EXPENSE))

    private fun subtitle(presets: List<QuickAddPreset>): String = if (presets.isEmpty()) {
        context.getString(R.string.quick_add_notification_description)
    } else {
        presets.take(MAX_PRESET_ACTIONS).joinToString(" · ") { it.label }
    }

    private fun presetActionLabel(preset: QuickAddPreset): String =
        "${preset.label} ${AMOUNT_FORMAT.format(preset.amount)}"

    private fun sheetIntent(type: TransactionType, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            appStarter.getQuickAddIntent(type),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun presetIntent(presetId: UUID, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, QuickAddNotificationReceiver::class.java).apply {
                action = QuickAddNotificationReceiver.ACTION_PRESET
                putExtra(QuickAddNotificationReceiver.EXTRA_PRESET_ID, presetId.toString())
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun undoIntent(transactionId: UUID): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_UNDO,
            Intent(context, QuickAddNotificationReceiver::class.java).apply {
                action = QuickAddNotificationReceiver.ACTION_UNDO
                putExtra(
                    QuickAddNotificationReceiver.EXTRA_TRANSACTION_ID,
                    transactionId.toString()
                )
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val NOTIFICATION_ID = 4242

        /** Three actions is all a collapsed notification shows; the first is always Income. */
        const val MAX_PRESET_ACTIONS = 2

        private const val REQUEST_EXPENSE = 4300
        private const val REQUEST_INCOME = 4301
        private const val REQUEST_UNDO = 4302
        private const val REQUEST_PRESET_BASE = 4310

        private val AMOUNT_FORMAT = DecimalFormat("###,###.##")
    }
}
