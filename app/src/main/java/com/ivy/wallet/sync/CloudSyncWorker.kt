package com.ivy.wallet.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ivy.data.sync.CloudSyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class CloudSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val cloudSyncRepository: CloudSyncRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return cloudSyncRepository.pushAll().fold(
            ifLeft = { error ->
                Timber.w("Cloud sync push failed: $error")
                Result.retry()
            },
            ifRight = { Result.success() }
        )
    }
}
