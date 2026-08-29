package com.ivy.wallet.budgetalert

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps [BudgetAlertWorker] running in the background.
 *
 * Six hours is deliberately unhurried. Crossing 80% of a budget is not an event that needs
 * to be known within the minute, and the worker only ever speaks once per threshold per
 * month, so checking more often would burn battery to say the same thing sooner.
 */
@Singleton
class BudgetAlertScheduler @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {

    fun schedule() {
        val request = PeriodicWorkRequestBuilder<BudgetAlertWorker>(
            PERIOD_HOURS,
            TimeUnit.HOURS,
        ).build()

        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            BudgetAlertWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        const val PERIOD_HOURS = 6L
    }
}
