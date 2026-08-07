package com.ivy.battery.domain

import com.ivy.battery.data.db.ChargeSessionEntity
import com.ivy.battery.data.db.DischargeSessionEntity
import com.ivy.battery.domain.model.BatterySnapshot
import com.ivy.battery.domain.model.HealthEstimate
import com.ivy.battery.domain.model.TimeEstimates
import kotlin.math.abs

/**
 * Aggregates individual charge measurements into a single battery-health figure.
 *
 * A single measurement is noisy (thermal throttling, trickle charging at the
 * top of the range, an OEM current sensor that rounds hard), so the reported
 * capacity is a weighted mean of recent measurements, weighted by how much of
 * the range each one covered, with implausible values dropped.
 */
object HealthCalculator {

    private const val MAX_MEASUREMENTS = 20
    private const val MIN_PLAUSIBLE_RATIO = 0.4
    private const val MAX_PLAUSIBLE_RATIO = 1.35
    private const val PERCENT = 100.0

    fun estimate(
        sessions: List<ChargeSessionEntity>,
        designCapacityMah: Double?,
    ): HealthEstimate {
        val cycles = sessions.sumOf { (it.endLevel - it.startLevel).coerceAtLeast(0) } / PERCENT

        val measurements = sessions
            .mapNotNull { session ->
                val capacity = session.estimatedCapacityMah ?: return@mapNotNull null
                if (!isPlausible(capacity, designCapacityMah)) return@mapNotNull null
                Measurement(
                    capacityMah = capacity,
                    weight = (session.endLevel - session.startLevel).toDouble(),
                    measuredAt = session.endedAt ?: session.startedAt,
                )
            }
            .sortedByDescending { it.measuredAt }
            .take(MAX_MEASUREMENTS)

        if (measurements.isEmpty()) {
            return HealthEstimate(
                designCapacityMah = designCapacityMah,
                estimatedCapacityMah = null,
                healthPercent = null,
                measurementCount = 0,
                chargeCycles = cycles,
                bestMeasurementMah = null,
                worstMeasurementMah = null,
                lastMeasuredAt = null,
            )
        }

        val totalWeight = measurements.sumOf { it.weight }
        val weightedCapacity = if (totalWeight > 0) {
            measurements.sumOf { it.capacityMah * it.weight } / totalWeight
        } else {
            measurements.map { it.capacityMah }.average()
        }

        return HealthEstimate(
            designCapacityMah = designCapacityMah,
            estimatedCapacityMah = weightedCapacity,
            healthPercent = designCapacityMah
                ?.takeIf { it > 0 }
                ?.let { (weightedCapacity / it) * PERCENT },
            measurementCount = measurements.size,
            chargeCycles = cycles,
            bestMeasurementMah = measurements.maxOf { it.capacityMah },
            worstMeasurementMah = measurements.minOf { it.capacityMah },
            lastMeasuredAt = measurements.first().measuredAt,
        )
    }

    private fun isPlausible(capacityMah: Double, designMah: Double?): Boolean {
        if (capacityMah <= 0) return false
        if (designMah == null || designMah <= 0) return true
        val ratio = capacityMah / designMah
        return ratio in MIN_PLAUSIBLE_RATIO..MAX_PLAUSIBLE_RATIO
    }

    private data class Measurement(
        val capacityMah: Double,
        val weight: Double,
        val measuredAt: Long,
    )
}

/**
 * Remaining-time predictions.
 *
 * Screen-on and screen-off runtime are predicted separately from the measured
 * history instead of averaging them together, because the two differ by an
 * order of magnitude and a blended number is useless for both.
 */
object TimeEstimator {

    private const val HOUR_MS = 60.0 * 60.0 * 1000.0
    private const val PERCENT = 100.0
    private const val MAX_HISTORY = 10
    private const val MIN_CURRENT_MA = 20.0

    @Suppress("LongParameterList")
    fun estimate(
        snapshot: BatterySnapshot,
        capacityMah: Double?,
        chargeSessions: List<ChargeSessionEntity>,
        dischargeSessions: List<DischargeSessionEntity>,
        chargeTargetLevel: Int,
    ): TimeEstimates {
        val remainingToFull = (PERCENT - snapshot.level).coerceAtLeast(0.0)
        val remainingToTarget = (chargeTargetLevel - snapshot.level).toDouble().coerceAtLeast(0.0)

        val chargeRatePerHour = chargeRatePercentPerHour(snapshot, capacityMah, chargeSessions)
        val screenOnRate = screenOnDrainPerHour(dischargeSessions)
        val screenOffRate = screenOffDrainPerHour(dischargeSessions)
        val liveRate = liveDrainPercentPerHour(snapshot, capacityMah)

        return TimeEstimates(
            timeToFullMs = durationFor(remainingToFull, chargeRatePerHour.takeIf {
                snapshot.charging
            }),
            timeToTargetMs = durationFor(remainingToTarget, chargeRatePerHour.takeIf {
                snapshot.charging && remainingToTarget > 0
            }),
            screenOnRemainingMs = durationFor(snapshot.level.toDouble(), screenOnRate),
            screenOffRemainingMs = durationFor(snapshot.level.toDouble(), screenOffRate),
            mixedRemainingMs = durationFor(
                snapshot.level.toDouble(),
                liveRate ?: blendedRate(screenOnRate, screenOffRate),
            ).takeIf { !snapshot.charging },
        )
    }

    /**
     * % per hour going in, preferring the live current reading and falling back
     * to what previous sessions on this charger actually achieved.
     */
    private fun chargeRatePercentPerHour(
        snapshot: BatterySnapshot,
        capacityMah: Double?,
        sessions: List<ChargeSessionEntity>,
    ): Double? {
        if (capacityMah != null && capacityMah > 0 && snapshot.currentMa > MIN_CURRENT_MA) {
            return (snapshot.currentMa / capacityMah) * PERCENT
        }
        val historical = sessions
            .filter { it.endedAt != null && it.durationMs > 0 && it.endLevel > it.startLevel }
            .take(MAX_HISTORY)
            .map { (it.endLevel - it.startLevel) * HOUR_MS / it.durationMs }
        return historical.takeIf { it.isNotEmpty() }?.average()
    }

    private fun liveDrainPercentPerHour(
        snapshot: BatterySnapshot,
        capacityMah: Double?,
    ): Double? {
        if (snapshot.charging) return null
        if (capacityMah == null || capacityMah <= 0) return null
        val drawMa = abs(snapshot.currentMa)
        if (drawMa < MIN_CURRENT_MA) return null
        return (drawMa / capacityMah) * PERCENT
    }

    private fun screenOnDrainPerHour(sessions: List<DischargeSessionEntity>): Double? =
        sessions
            .filter { it.screenOnMs > MIN_SEGMENT_MS && it.screenOnDrainPercent > 0 }
            .take(MAX_HISTORY)
            .map { it.screenOnDrainPercent * HOUR_MS / it.screenOnMs }
            .takeIf { it.isNotEmpty() }
            ?.average()

    private fun screenOffDrainPerHour(sessions: List<DischargeSessionEntity>): Double? =
        sessions
            .filter { it.screenOffMs > MIN_SEGMENT_MS && it.screenOffDrainPercent > 0 }
            .take(MAX_HISTORY)
            .map { it.screenOffDrainPercent * HOUR_MS / it.screenOffMs }
            .takeIf { it.isNotEmpty() }
            ?.average()

    /** A typical day is far more screen-off than screen-on; weight it that way. */
    private fun blendedRate(screenOn: Double?, screenOff: Double?): Double? = when {
        screenOn != null && screenOff != null -> screenOn * 0.25 + screenOff * 0.75
        else -> screenOn ?: screenOff
    }

    private fun durationFor(percent: Double, ratePerHour: Double?): Long? {
        if (ratePerHour == null || ratePerHour <= 0 || percent <= 0) return null
        return ((percent / ratePerHour) * HOUR_MS).toLong()
    }

    private const val MIN_SEGMENT_MS = 5L * 60L * 1000L
}
