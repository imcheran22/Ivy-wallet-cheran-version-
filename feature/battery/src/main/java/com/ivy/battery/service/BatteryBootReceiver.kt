package com.ivy.battery.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Restarts battery monitoring after a reboot or an app update.
 *
 * It only enqueues work - the worker checks whether monitoring is actually
 * enabled and takes it from there, which keeps `onReceive` well inside its
 * execution budget and avoids a foreground-service start from a receiver.
 */
class BatteryBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val workManager = WorkManager.getInstance(context)
        workManager.enqueue(OneTimeWorkRequestBuilder<BatteryMonitorWorker>().build())
        workManager.enqueueUniquePeriodicWork(
            KEEP_ALIVE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<BatteryMonitorWorker>(
                KEEP_ALIVE_MINUTES,
                TimeUnit.MINUTES,
            ).build(),
        )
    }

    private companion object {
        const val KEEP_ALIVE_WORK = "ivy_battery_monitor_keepalive"
        const val KEEP_ALIVE_MINUTES = 15L
    }
}
