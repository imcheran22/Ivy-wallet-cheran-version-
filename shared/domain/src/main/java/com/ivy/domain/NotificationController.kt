package com.ivy.domain

/**
 * Lets any screen turn the app's background notifications on and off without depending on the
 * Android plumbing that implements them.
 */
interface NotificationController {
    /** Posts, updates or dismisses the ongoing quick-add notification to match its setting. */
    suspend fun refreshQuickAddNotification()

    fun scheduleDailySummary()

    fun cancelDailySummary()
}
