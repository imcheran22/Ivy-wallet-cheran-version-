package com.ivy.wallet.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ivy.data.sync.CloudSyncRepository
import com.ivy.data.sync.CloudSyncSettings
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class CloudSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val cloudSyncRepository: CloudSyncRepository,
    private val cloudSyncSettings: CloudSyncSettings,
) : CoroutineWorker(appContext, params) {

    /**
     * Merges rather than pushes: a background job that only ever pushed would quietly undo
     * whatever the paired device did between runs.
     */
    override suspend fun doWork(): Result {
        return cloudSyncRepository.sync().fold(
            ifLeft = { error ->
                Timber.w("Cloud sync failed: $error")
                cloudSyncSettings.setLastResult(error)
                Result.retry()
            },
            ifRight = { result ->
                cloudSyncSettings.setLastResult(
                    "Pulled ${result.pulled}, pushed ${result.pushed}"
                )
                Result.success()
            }
        )
    }
}
