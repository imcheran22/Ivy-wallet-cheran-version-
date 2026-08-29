package com.ivy.wallet.quickadd.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Puts the quick-add notification and the evening summary back after a reboot.
 *
 * Notifications don't survive a restart, and a "permanent" button that quietly disappears when
 * the phone reboots is worse than not offering one.
 */
@AndroidEntryPoint
class QuickAddBootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationManager: QuickAddNotificationManager

    @Inject
    lateinit var dailySummaryScheduler: DailySummaryScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                notificationManager.refresh()
                dailySummaryScheduler.schedule()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
