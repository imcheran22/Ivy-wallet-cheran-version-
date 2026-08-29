package com.ivy.wallet.budgetalert

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ivy.data.db.dao.read.BudgetDao
import com.ivy.data.model.Expense
import com.ivy.data.repository.TransactionRepository
import com.ivy.domain.AppStarter
import com.ivy.wallet.android.notification.IvyNotificationChannel
import com.ivy.wallet.android.notification.NotificationService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

/**
 * Says something when a budget is nearly gone, instead of waiting to be asked.
 *
 * A budget you have to open the app to check is a budget you find out about afterwards. The
 * two moments worth interrupting for are the one where there is still time to change course
 * and the one where the line has been crossed - 80% and 100%, once each per budget per month.
 *
 * What has already been announced is remembered per budget, month and threshold, because a
 * worker that re-runs every few hours would otherwise say the same thing all week and be
 * turned off within a day.
 */
@HiltWorker
class BudgetAlertWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val budgetDao: BudgetDao,
    private val transactionRepository: TransactionRepository,
    private val notificationService: NotificationService,
    private val appStarter: AppStarter,
    private val dataStore: DataStore<Preferences>,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val budgets = budgetDao.findAll()
        if (budgets.isEmpty()) return@withContext Result.success()

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant()
        val monthEnd = today.atTime(23, 59, 59).atZone(zone).toInstant()
        val month = YearMonth.from(today).toString()

        val expenses = transactionRepository.findAllBetween(monthStart, monthEnd)
            .filterIsInstance<Expense>()

        val announced = dataStore.data.first()[announcedKey].orEmpty().toMutableSet()
        var changed = false

        budgets.forEach { budget ->
            if (budget.amount <= 0.0) return@forEach

            val categoryIds = parseIds(budget.categoryIdsSerialized)
            val accountIds = parseIds(budget.accountIdsSerialized)

            val spent = expenses.filter { expense ->
                val categoryMatches = categoryIds.isEmpty() ||
                    expense.category?.value in categoryIds
                val accountMatches = accountIds.isEmpty() || expense.account.value in accountIds
                categoryMatches && accountMatches
            }.sumOf { it.value.amount.value }

            val threshold = when {
                spent >= budget.amount -> OVER
                spent >= budget.amount * NEARLY -> NEARLY
                else -> return@forEach
            }

            val marker = "${budget.id}:$month:$threshold"
            if (marker in announced) return@forEach

            notify(budget.name, spent, budget.amount, threshold, budget.id)
            announced += marker
            changed = true
        }

        if (changed) {
            // Only this month's markers are worth keeping; the rest would grow forever.
            dataStore.edit { prefs ->
                prefs[announcedKey] = announced.filter { it.contains(":$month:") }.toSet()
            }
        }

        Result.success()
    }

    private fun notify(
        name: String,
        spent: Double,
        limit: Double,
        threshold: Double,
        budgetId: UUID,
    ) {
        val percent = ((spent / limit) * PERCENT).toInt()
        val notification = notificationService
            .defaultIvyNotification(
                channel = IvyNotificationChannel.TRANSACTION_REMINDER,
                priority = NotificationCompat.PRIORITY_DEFAULT,
            )
            .setContentTitle(
                if (threshold >= OVER) "$name is over budget" else "$name is nearly spent"
            )
            .setContentText("$percent% used of the month's budget.")
            .setContentIntent(
                PendingIntent.getActivity(
                    applicationContext,
                    budgetId.hashCode(),
                    appStarter.getRootIntent(),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )

        notificationService.showNotification(notification, budgetId.hashCode())
    }

    private fun parseIds(serialized: String?): Set<UUID> {
        if (serialized.isNullOrBlank()) return emptySet()
        return serialized.split(",").mapNotNull {
            runCatching { UUID.fromString(it.trim()) }.getOrNull()
        }.toSet()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "budget_alerts"

        private const val NEARLY = 0.8
        private const val OVER = 1.0
        private const val PERCENT = 100

        private val announcedKey = stringSetPreferencesKey("budget_alerts_announced")
    }
}
