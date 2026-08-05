package com.ivy.wallet.sms

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ivy.data.datastore.DatastoreKeys
import com.ivy.domain.sync.CloudSyncTrigger
import com.ivy.domain.usecase.sms.ImportSmsTransactionUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant

@HiltWorker
class SmsImportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val importSmsTransactionUseCase: ImportSmsTransactionUseCase,
    private val dataStore: DataStore<Preferences>,
    private val cloudSyncTrigger: CloudSyncTrigger,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val INPUT_SMS_BODY = "sms_body"
        const val INPUT_RECEIVED_AT_EPOCH_MILLIS = "received_at_epoch_millis"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val autoImportEnabled = dataStore.data.first()[DatastoreKeys.SMS_AUTO_IMPORT_ENABLED] ?: false
        if (!autoImportEnabled) return@withContext Result.success()

        val body = inputData.getString(INPUT_SMS_BODY) ?: return@withContext Result.success()
        val receivedAtEpochMillis = inputData.getLong(INPUT_RECEIVED_AT_EPOCH_MILLIS, -1)
        val receivedAt = if (receivedAtEpochMillis > 0) {
            Instant.ofEpochMilli(receivedAtEpochMillis)
        } else {
            Instant.now()
        }

        val result = importSmsTransactionUseCase.import(smsBody = body, receivedAt = receivedAt)
        if (result is ImportSmsTransactionUseCase.Result.Imported) {
            // Best-effort: push the new transaction to the cloud, but a sync hiccup
            // shouldn't make the SMS import itself look like it failed.
            runCatching { cloudSyncTrigger.syncNow() }
        }

        Result.success()
    }
}
