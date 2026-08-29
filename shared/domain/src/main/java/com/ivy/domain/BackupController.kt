package com.ivy.domain

/**
 * Starts and stops the automatic backup job without exposing WorkManager to the screens that
 * turn it on.
 */
interface BackupController {
    fun schedule()
    fun cancel()
    fun backUpNow()
}
