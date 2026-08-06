package com.ivy.battery.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Captures the exact moment a charger is plugged in or pulled out.
 *
 * These two transitions are the boundaries of every charge and discharge
 * measurement, so they must be recorded even when the sampling service isn't
 * running - a missed unplug would merge two sessions and ruin the capacity
 * estimate for both.
 */
class PowerConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_POWER_CONNECTED &&
            intent.action != Intent.ACTION_POWER_DISCONNECTED
        ) {
            return
        }

        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<BatteryMonitorWorker>().build()
        )
    }
}
