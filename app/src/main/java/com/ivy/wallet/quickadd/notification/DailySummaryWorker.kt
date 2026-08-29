package com.ivy.wallet.quickadd.notification

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ivy.data.datastore.DatastoreKeys
import com.ivy.domain.AppStarter
import com.ivy.domain.usecase.summary.DailySummary
import com.ivy.domain.usecase.summary.DailySummaryUseCase
import com.ivy.ui.R
import com.ivy.wallet.android.notification.IvyNotificationChannel
import com.ivy.wallet.android.notification.NotificationService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.text.DecimalFormat

/**
 * The evening recap.
 *
 * It only fires on days that had transactions, and it leads with what still needs sorting,
 * because that is the only part the user can act on tonight. A summary that shows up after a
 * quiet day is just noise, and noise is how a useful notification gets turned off.
 */
@HiltWorker
class DailySummaryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dailySummaryUseCase: DailySummaryUseCase,
    private val notificationService: NotificationService,
    private val appStarter: AppStarter,
    private val dataStore: DataStore<Preferences>,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val enabled = dataStore.data.first()[DatastoreKeys.DAILY_SUMMARY_ENABLED] ?: false
        if (!enabled) return Result.success()

        val summary = runCatching { dailySummaryUseCase.today() }.getOrNull()
            ?: return Result.success()
        if (summary.isEmpty) return Result.success()

        val notification = notificationService
            .defaultIvyNotification(
                channel = IvyNotificationChannel.DAILY_SUMMARY,
                priority = NotificationCompat.PRIORITY_DEFAULT,
            )
            .setContentTitle(title(summary))
            .setContentText(body(summary))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body(summary)))
            .setContentIntent(contentIntent(summary))

        notificationService.showNotification(notification, NOTIFICATION_ID)
        return Result.success()
    }

    private fun title(summary: DailySummary): String = applicationContext.getString(
        R.string.daily_summary_title,
        "${AMOUNT_FORMAT.format(summary.spent)} ${summary.currency}",
        summary.transactionCount,
    )

    private fun body(summary: DailySummary): String = if (summary.uncategorizedCount > 0) {
        applicationContext.resources.getQuantityString(
            R.plurals.daily_summary_uncategorized,
            summary.uncategorizedCount,
            summary.uncategorizedCount,
        )
    } else {
        applicationContext.getString(R.string.daily_summary_all_sorted)
    }

    private fun contentIntent(summary: DailySummary): PendingIntent = PendingIntent.getActivity(
        applicationContext,
        REQUEST_CODE,
        if (summary.uncategorizedCount > 0) {
            appStarter.getSortingQueueIntent()
        } else {
            appStarter.getRootIntent()
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val NOTIFICATION_ID = 4243
        private const val REQUEST_CODE = 4320
        private val AMOUNT_FORMAT = DecimalFormat("###,###.##")
    }
}
