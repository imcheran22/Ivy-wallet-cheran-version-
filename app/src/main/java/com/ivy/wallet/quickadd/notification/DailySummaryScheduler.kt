package com.ivy.wallet.quickadd.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ivy.base.time.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs [DailySummaryWorker] once an evening.
 */
@Singleton
class DailySummaryScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider,
) {

    fun schedule() {
        val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(
            REPEAT_HOURS,
            TimeUnit.HOURS,
        ).setInitialDelay(initialDelayMinutes(), TimeUnit.MINUTES).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun initialDelayMinutes(): Long {
        val now = timeProvider.localNow()
        val todayAtSummaryTime = now.toLocalDate().atTime(SUMMARY_TIME)
        val next = if (todayAtSummaryTime.isAfter(now)) {
            todayAtSummaryTime
        } else {
            todayAtSummaryTime.plusDays(1)
        }
        return Duration.between(now, next).toMinutes().coerceAtLeast(0)
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "daily_summary_work"
        private const val REPEAT_HOURS = 24L

        /** Late enough that the day is done, early enough to still act on it. */
        private val SUMMARY_TIME: LocalTime = LocalTime.of(21, 0)
    }
}
