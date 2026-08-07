package com.ivy.battery.domain

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.ivy.battery.data.db.AppDrainEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attributes a discharge session's screen-on drain to individual apps.
 *
 * Android does not expose per-app battery accounting to third-party apps
 * (`BATTERY_STATS` is a system permission), so this does what every battery
 * app does: it measures how long each app actually held the foreground during
 * the session via usage-access, then splits the measured screen-on mAh in that
 * proportion. It is an attribution model, not a hardware measurement, and the
 * UI says so.
 */
@Singleton
class AppDrainEstimator @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasUsageAccess(): Boolean = runCatching {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun usageAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * @param screenOnMah measured screen-on drain of the session, split across apps.
     * @param screenOnDrainPercent measured screen-on % drop, split the same way.
     */
    fun estimate(
        sessionId: String,
        from: Long,
        to: Long,
        screenOnMah: Double,
        screenOnDrainPercent: Double,
    ): List<AppDrainEntity> {
        if (!hasUsageAccess() || to <= from) return emptyList()

        val foregroundMsByPackage = foregroundTimeByPackage(from, to)
        val total = foregroundMsByPackage.values.sum()
        if (total <= 0) return emptyList()

        val now = System.currentTimeMillis()
        return foregroundMsByPackage
            .filterValues { it >= MIN_FOREGROUND_MS }
            .map { (packageName, foregroundMs) ->
                val share = foregroundMs.toDouble() / total
                AppDrainEntity(
                    sessionId = sessionId,
                    packageName = packageName,
                    foregroundMs = foregroundMs,
                    estimatedMah = screenOnMah * share,
                    estimatedPercent = screenOnDrainPercent * share,
                    updatedAt = now,
                )
            }
            .sortedByDescending { it.estimatedMah }
    }

    /**
     * Walks the usage-event stream and pairs each "moved to foreground" with the
     * matching "moved to background", so an app that was left open but unused
     * doesn't get billed for the whole window.
     */
    @Suppress("NestedBlockDepth")
    fun foregroundTimeByPackage(from: Long, to: Long): Map<String, Long> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
                as? UsageStatsManager ?: return emptyMap()

        val result = mutableMapOf<String, Long>()
        val startedAt = mutableMapOf<String, Long>()

        runCatching {
            val events = usageStatsManager.queryEvents(from, to)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND ->
                        startedAt[pkg] = event.timeStamp

                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        val start = startedAt.remove(pkg) ?: continue
                        val duration = (event.timeStamp - start).coerceAtLeast(0L)
                        result[pkg] = (result[pkg] ?: 0L) + duration
                    }

                    else -> Unit
                }
            }
            // Whatever is still in the foreground when the window closes.
            startedAt.forEach { (pkg, start) ->
                result[pkg] = (result[pkg] ?: 0L) + (to - start).coerceAtLeast(0L)
            }
        }.onFailure {
            Timber.w(it, "Battery: failed to read usage events")
        }

        return result
    }

    fun appLabel(packageName: String): String = runCatching {
        val packageManager = context.packageManager
        packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(packageName, 0)
        ).toString()
    }.getOrDefault(packageName)

    companion object {
        private const val MIN_FOREGROUND_MS = 1000L
    }
}
