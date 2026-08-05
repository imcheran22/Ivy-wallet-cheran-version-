package com.ivy.domain.sync

/**
 * A component used to schedule/trigger cloud sync work (WorkManager) without the caller
 * needing to depend on the `app` module, where the actual Worker lives.
 */
interface CloudSyncTrigger {
    /** Enqueues a one-off push, e.g. right after the user saves credentials or an SMS import. */
    fun syncNow()

    /** (Re)schedules the periodic background sync. Safe to call repeatedly. */
    fun schedulePeriodic()

    /** Cancels the periodic background sync, e.g. when the user disables cloud sync. */
    fun cancelPeriodic()
}
