package com.ivy.battery.data

import android.os.BatteryManager
import com.ivy.battery.data.db.AppDrainAggregate
import com.ivy.battery.data.db.BatteryDatabase
import com.ivy.battery.data.db.BatterySampleEntity
import com.ivy.battery.data.db.ChargeSessionEntity
import com.ivy.battery.data.db.DischargeSessionEntity
import com.ivy.battery.domain.AppDrainEstimator
import com.ivy.battery.domain.DesignCapacityProvider
import com.ivy.battery.domain.HealthCalculator
import com.ivy.battery.domain.SessionAggregator
import com.ivy.battery.domain.TimeEstimator
import com.ivy.battery.domain.model.BatterySnapshot
import com.ivy.battery.domain.model.HealthEstimate
import com.ivy.battery.domain.model.TimeEstimates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What changed as a result of recording a sample, so callers (the monitor
 * service) can react without re-querying.
 */
data class RecordOutcome(
    val chargeSession: ChargeSessionEntity?,
    val dischargeSession: DischargeSessionEntity?,
    val startedCharging: Boolean,
    val stoppedCharging: Boolean,
)

/**
 * Owns battery telemetry: appends samples and keeps exactly one open charge or
 * discharge session in sync with them.
 *
 * Session statistics are always recomputed from the underlying samples rather
 * than accumulated incrementally - it costs one bounded query per sample and
 * means a killed process, a reboot or a settings change can never leave a
 * half-updated running total behind.
 */
@Singleton
class BatteryRepository @Inject constructor(
    private val db: BatteryDatabase,
    private val preferences: BatteryPreferences,
    private val designCapacityProvider: DesignCapacityProvider,
    private val appDrainEstimator: AppDrainEstimator,
) {
    private val recordMutex = Mutex()

    suspend fun record(snapshot: BatterySnapshot): RecordOutcome = recordMutex.withLock {
        val settings = preferences.current()
        db.sampleDao.insert(snapshot.toEntity())

        val openCharge = db.chargeSessionDao.openSession()
        val openDischarge = db.dischargeSessionDao.openSession()

        if (snapshot.charging) {
            val stoppedDischarging = openDischarge != null
            if (openDischarge != null) {
                closeDischargeSession(openDischarge, snapshot.timestamp, settings)
            }
            val session = updateChargeSession(openCharge, snapshot, settings)
            RecordOutcome(
                chargeSession = session,
                dischargeSession = null,
                startedCharging = stoppedDischarging || openCharge == null,
                stoppedCharging = false,
            )
        } else {
            val wasCharging = openCharge != null
            if (openCharge != null) {
                closeChargeSession(openCharge, snapshot.timestamp, settings)
            }
            val session = updateDischargeSession(openDischarge, snapshot)
            RecordOutcome(
                chargeSession = null,
                dischargeSession = session,
                startedCharging = false,
                stoppedCharging = wasCharging,
            )
        }
    }

    /**
     * The samples that belong to a session of the given kind.
     *
     * The sample recorded at a transition belongs to the *new* session, so it
     * is filtered out of the one being closed - otherwise the first sample
     * after plugging in would be averaged into the discharge that just ended.
     */
    private suspend fun samplesFor(
        startedAt: Long,
        endAt: Long,
        charging: Boolean,
    ): List<BatterySampleEntity> = db.sampleDao.between(startedAt, endAt)
        .filter { it.isCharging() == charging }

    private fun BatterySampleEntity.isCharging(): Boolean =
        plugged != 0 &&
                (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL)

    private suspend fun updateChargeSession(
        open: ChargeSessionEntity?,
        snapshot: BatterySnapshot,
        settings: BatterySettings,
    ): ChargeSessionEntity? {
        val id = open?.id ?: UUID.randomUUID().toString()
        val startedAt = open?.startedAt ?: snapshot.timestamp
        val samples = samplesFor(startedAt, snapshot.timestamp, charging = true)
        val aggregated = SessionAggregator.aggregateCharge(
            id = id,
            samples = samples,
            endedAt = null,
            designCapacityMah = designCapacityProvider.designCapacityMah(),
            minLevelDelta = settings.minLevelDeltaForCapacity,
        ) ?: return open
        db.chargeSessionDao.upsert(aggregated)
        return aggregated
    }

    private suspend fun updateDischargeSession(
        open: DischargeSessionEntity?,
        snapshot: BatterySnapshot,
    ): DischargeSessionEntity? {
        val id = open?.id ?: UUID.randomUUID().toString()
        val startedAt = open?.startedAt ?: snapshot.timestamp
        val samples = samplesFor(startedAt, snapshot.timestamp, charging = false)
        val aggregated = SessionAggregator.aggregateDischarge(
            id = id,
            samples = samples,
            endedAt = null,
        ) ?: return open
        db.dischargeSessionDao.upsert(aggregated)
        return aggregated
    }

    private suspend fun closeChargeSession(
        open: ChargeSessionEntity,
        endedAt: Long,
        settings: BatterySettings,
    ) {
        val samples = samplesFor(open.startedAt, endedAt, charging = true)
        val finalized = SessionAggregator.aggregateCharge(
            id = open.id,
            samples = samples,
            endedAt = endedAt,
            designCapacityMah = designCapacityProvider.designCapacityMah(),
            minLevelDelta = settings.minLevelDeltaForCapacity,
        ) ?: open.copy(endedAt = endedAt)
        db.chargeSessionDao.upsert(finalized)
        Timber.d(
            "Battery: charge session closed %d%% -> %d%%, %.0f mAh, capacity %s",
            finalized.startLevel,
            finalized.endLevel,
            finalized.chargedMah,
            finalized.estimatedCapacityMah?.let { "%.0f mAh".format(it) } ?: "not measurable",
        )
    }

    private suspend fun closeDischargeSession(
        open: DischargeSessionEntity,
        endedAt: Long,
        settings: BatterySettings,
    ) {
        val samples = samplesFor(open.startedAt, endedAt, charging = false)
        val finalized = SessionAggregator.aggregateDischarge(
            id = open.id,
            samples = samples,
            endedAt = endedAt,
        ) ?: open.copy(endedAt = endedAt)
        db.dischargeSessionDao.upsert(finalized)

        if (settings.trackAppDrain) {
            updateAppDrain(finalized)
        }
    }

    /**
     * Recomputes the per-app split for a session. Safe to call for the session
     * that is still running - the rows are keyed by session id and replaced.
     */
    suspend fun updateAppDrain(session: DischargeSessionEntity) {
        val rows = appDrainEstimator.estimate(
            sessionId = session.id,
            from = session.startedAt,
            to = session.endedAt ?: System.currentTimeMillis(),
            screenOnMah = session.screenOnMah,
            screenOnDrainPercent = session.screenOnDrainPercent,
        )
        if (rows.isEmpty()) return
        db.appDrainDao.deleteForSession(session.id)
        db.appDrainDao.upsertAll(rows)
    }

    suspend fun refreshOngoingAppDrain() {
        val open = db.dischargeSessionDao.openSession() ?: return
        updateAppDrain(open)
    }

    suspend fun openChargeSession(): ChargeSessionEntity? = db.chargeSessionDao.openSession()

    suspend fun openDischargeSession(): DischargeSessionEntity? =
        db.dischargeSessionDao.openSession()

    fun chargeSessionsFlow(limit: Int = DEFAULT_SESSION_LIMIT): Flow<List<ChargeSessionEntity>> =
        db.chargeSessionDao.recentFlow(limit)

    fun dischargeSessionsFlow(
        limit: Int = DEFAULT_SESSION_LIMIT
    ): Flow<List<DischargeSessionEntity>> = db.dischargeSessionDao.recentFlow(limit)

    fun samplesSinceFlow(from: Long): Flow<List<BatterySampleEntity>> =
        db.sampleDao.sinceFlow(from)

    fun appDrainSinceFlow(from: Long): Flow<List<AppDrainAggregate>> =
        db.appDrainDao.aggregatedSinceFlow(from)

    fun sampleCountFlow(): Flow<Int> = db.sampleDao.countFlow()

    /**
     * Health has to fold in the design capacity, which can only be read
     * asynchronously, so it is derived here rather than in the ViewModel.
     */
    fun healthFlow(): Flow<HealthEstimate> = db.chargeSessionDao.allFlow().map { sessions ->
        HealthCalculator.estimate(sessions, designCapacityProvider.designCapacityMah())
    }

    suspend fun designCapacityMah(): Double? = designCapacityProvider.designCapacityMah()

    /**
     * The capacity to reason about *today*: the measured one when we have
     * enough samples, otherwise the design figure.
     */
    suspend fun effectiveCapacityMah(): Double? {
        val design = designCapacityProvider.designCapacityMah()
        val measured = HealthCalculator.estimate(db.chargeSessionDao.all(), design)
        return measured.estimatedCapacityMah ?: design
    }

    suspend fun estimates(
        snapshot: BatterySnapshot,
        chargeTargetLevel: Int,
    ): TimeEstimates = TimeEstimator.estimate(
        snapshot = snapshot,
        capacityMah = effectiveCapacityMah(),
        chargeSessions = db.chargeSessionDao.all(),
        dischargeSessions = db.dischargeSessionDao.all(),
        chargeTargetLevel = chargeTargetLevel,
    )

    suspend fun systemDesignCapacityMah(): Double? = designCapacityProvider.systemDesignCapacityMah()

    suspend fun pruneOldData() {
        val retentionDays = preferences.current().retentionDays
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
        db.sampleDao.deleteOlderThan(cutoff)
        db.chargeSessionDao.deleteOlderThan(cutoff)
        db.dischargeSessionDao.deleteOlderThan(cutoff)
        db.appDrainDao.deleteOlderThan(cutoff)
    }

    suspend fun clearAll() {
        db.appDrainDao.deleteAll()
        db.chargeSessionDao.deleteAll()
        db.dischargeSessionDao.deleteAll()
        db.sampleDao.deleteAll()
    }

    /**
     * Everything measured so far as CSV, so the data isn't trapped in the app.
     */
    suspend fun exportCsv(): String = buildString {
        appendLine("# charge sessions")
        appendLine(
            "started_at,ended_at,start_level,end_level,charged_mah," +
                    "estimated_capacity_mah,health_percent,avg_current_ma,max_current_ma," +
                    "avg_temp_c,max_temp_c,duration_ms"
        )
        db.chargeSessionDao.all().forEach {
            appendLine(
                listOf(
                    it.startedAt,
                    it.endedAt ?: "",
                    it.startLevel,
                    it.endLevel,
                    "%.1f".format(it.chargedMah),
                    it.estimatedCapacityMah?.let { value -> "%.1f".format(value) } ?: "",
                    it.healthPercent?.let { value -> "%.1f".format(value) } ?: "",
                    "%.1f".format(it.avgCurrentMa),
                    "%.1f".format(it.maxCurrentMa),
                    "%.1f".format(it.avgTemperatureC),
                    "%.1f".format(it.maxTemperatureC),
                    it.durationMs,
                ).joinToString(",")
            )
        }

        appendLine()
        appendLine("# discharge sessions")
        appendLine(
            "started_at,ended_at,start_level,end_level,screen_on_ms,screen_off_ms," +
                    "screen_on_drain_percent,screen_off_drain_percent,screen_on_mah," +
                    "screen_off_mah,deep_sleep_ms,awake_ms,duration_ms"
        )
        db.dischargeSessionDao.all().forEach {
            appendLine(
                listOf(
                    it.startedAt,
                    it.endedAt ?: "",
                    it.startLevel,
                    it.endLevel,
                    it.screenOnMs,
                    it.screenOffMs,
                    "%.2f".format(it.screenOnDrainPercent),
                    "%.2f".format(it.screenOffDrainPercent),
                    "%.1f".format(it.screenOnMah),
                    "%.1f".format(it.screenOffMah),
                    it.deepSleepMs,
                    it.awakeMs,
                    it.durationMs,
                ).joinToString(",")
            )
        }
    }

    companion object {
        const val DEFAULT_SESSION_LIMIT = 100
    }
}
