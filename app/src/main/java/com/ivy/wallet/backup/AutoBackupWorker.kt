package com.ivy.wallet.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ivy.data.backup.BackupDataUseCase
import com.ivy.data.datastore.DatastoreKeys
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.File
import java.time.LocalDate

/**
 * Writes a full JSON backup to the app's own external files directory, on a schedule.
 *
 * The manual backup already existed, but it needs someone to remember to run it - and the day
 * you need a backup is never a day you remembered. This one keeps the last few and drops the
 * rest, so it can run forever without filling the phone.
 *
 * Deliberately app-private storage: no permissions, cleaned up when the app is uninstalled,
 * and still reachable over USB or a file manager for anyone who wants to copy one out.
 */
@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val backupDataUseCase: BackupDataUseCase,
    private val dataStore: DataStore<Preferences>,
) : CoroutineWorker(appContext, params) {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result {
        val enabled = dataStore.data.first()[DatastoreKeys.AUTO_BACKUP_ENABLED] ?: false
        if (!enabled) return Result.success()

        return try {
            val directory = backupDirectory() ?: return Result.retry()
            val file = File(directory, "ivy-backup-${LocalDate.now()}.json")
            file.writeText(backupDataUseCase.generateJsonBackup())
            prune(directory)
            record("Saved ${file.name}")
            Result.success()
        } catch (e: Exception) {
            Timber.w(e, "Automatic backup failed")
            record(e.message ?: "Backup failed")
            Result.retry()
        }
    }

    private fun backupDirectory(): File? =
        applicationContext.getExternalFilesDir(BACKUP_DIRECTORY)?.apply { mkdirs() }

    /** Keeps a week of history. Older ones are of no use once a newer one exists. */
    private fun prune(directory: File) {
        directory.listFiles { file -> file.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_BACKUPS)
            ?.forEach { it.delete() }
    }

    private suspend fun record(result: String) {
        dataStore.edit {
            it[DatastoreKeys.AUTO_BACKUP_LAST_RUN_EPOCH_MS] = System.currentTimeMillis()
            it[DatastoreKeys.AUTO_BACKUP_LAST_RESULT] = result
        }
    }

    companion object {
        const val BACKUP_DIRECTORY = "backups"
        const val MAX_BACKUPS = 7
    }
}
