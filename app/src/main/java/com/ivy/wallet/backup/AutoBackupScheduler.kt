package com.ivy.wallet.backup

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ivy.domain.BackupController
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoBackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : BackupController {

    override fun schedule() {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(
            REPEAT_HOURS,
            TimeUnit.HOURS,
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    override fun backUpNow() {
        WorkManager.getInstance(context)
            .enqueue(OneTimeWorkRequestBuilder<AutoBackupWorker>().build())
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "auto_backup_work"
        private const val REPEAT_HOURS = 24L
    }
}
