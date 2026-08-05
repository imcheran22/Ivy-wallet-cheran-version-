package com.ivy.battery.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ivy.battery.data.BatteryPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place that decides whether the battery monitor should be running.
 *
 * Two mechanisms back each other up: a foreground service for second-by-second
 * sampling, and a periodic worker that both restarts the service after the
 * system kills it and records a sample on its own, so a device that refuses
 * the foreground start still produces (coarser) history.
 */
@Singleton
class BatteryMonitorController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: BatteryPreferences,
) {
    fun startService() {
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BatteryMonitorService::class.java)
                    .setAction(BatteryMonitorService.ACTION_START),
            )
        }.onFailure {
            Timber.w(it, "Battery: monitor service could not be started")
        }
    }

    fun stopService() {
        runCatching {
            context.startService(
                Intent(context, BatteryMonitorService::class.java)
                    .setAction(BatteryMonitorService.ACTION_STOP),
            )
        }.onFailure {
            // Nothing to stop; the OS already killed it.
            Timber.d("Battery: monitor service was not running")
        }
    }

    fun schedulePeriodicWork() {
        val request = PeriodicWorkRequestBuilder<BatteryMonitorWorker>(
            KEEP_ALIVE_MINUTES,
            TimeUnit.MINUTES,
        ).setConstraints(Constraints.NONE).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancelPeriodicWork() {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    fun sampleNow() {
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<BatteryMonitorWorker>().build()
        )
    }

    /** Brings the running state in line with the user's preference. */
    suspend fun applySettings() {
        if (preferences.current().monitoringEnabled) {
            startService()
            schedulePeriodicWork()
        } else {
            stopService()
            cancelPeriodicWork()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "ivy_battery_monitor_keepalive"
        private const val KEEP_ALIVE_MINUTES = 15L
    }
}
