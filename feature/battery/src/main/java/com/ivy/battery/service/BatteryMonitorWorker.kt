package com.ivy.battery.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ivy.battery.data.BatteryPreferences
import com.ivy.battery.data.BatteryRepository
import com.ivy.battery.domain.BatteryReader
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * The monitor's safety net: records a sample and makes sure the sampling
 * service is alive.
 *
 * It runs periodically and on plug/unplug, so even when the foreground service
 * is denied or killed (aggressive OEM power management, a background start
 * refused on Android 12+) the charge/discharge history keeps advancing.
 */
@HiltWorker
class BatteryMonitorWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val reader: BatteryReader,
    private val repository: BatteryRepository,
    private val preferences: BatteryPreferences,
    private val controller: BatteryMonitorController,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = preferences.current()
        if (!settings.monitoringEnabled) {
            controller.cancelPeriodicWork()
            return Result.success()
        }

        return runCatching {
            val snapshot = reader.read(
                broadcast = null,
                unitMode = settings.currentUnitMode,
                invertSign = settings.invertCurrentSign,
            )
            repository.record(snapshot)
            repository.pruneOldData()
            controller.startService()
            Result.success()
        }.getOrElse {
            Timber.w(it, "Battery: keep-alive sampling failed")
            Result.success()
        }
    }
}
