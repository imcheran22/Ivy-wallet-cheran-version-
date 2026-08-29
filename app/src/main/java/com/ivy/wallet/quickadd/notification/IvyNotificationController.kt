package com.ivy.wallet.quickadd.notification

import com.ivy.domain.NotificationController
import javax.inject.Inject

class IvyNotificationController @Inject constructor(
    private val quickAddNotificationManager: QuickAddNotificationManager,
    private val dailySummaryScheduler: DailySummaryScheduler,
) : NotificationController {

    override suspend fun refreshQuickAddNotification() {
        quickAddNotificationManager.refresh()
    }

    override fun scheduleDailySummary() {
        dailySummaryScheduler.schedule()
    }

    override fun cancelDailySummary() {
        dailySummaryScheduler.cancel()
    }
}
