package com.ivy.battery.domain

import com.ivy.battery.data.db.BatterySampleEntity
import com.ivy.battery.data.db.ChargeSessionEntity
import com.ivy.battery.data.db.DischargeSessionEntity
import kotlin.math.abs

/**
 * Turns a run of raw samples into session statistics.
 *
 * Everything is computed pair-by-pair rather than from the session's endpoints:
 * that keeps the numbers correct across reboots (the monotonic clocks reset),
 * and lets a suspiciously long gap between two samples be clamped instead of
 * silently inflating a screen-off time or a deep-sleep figure.
 */
object SessionAggregator {

    /** A gap longer than this means we weren't sampling; don't attribute it to anything. */
    private const val MAX_GAP_MS = 15L * 60L * 1000L
    private const val HOUR_MS = 60.0 * 60.0 * 1000.0
    private const val MICRO_AH_PER_MAH = 1000.0
    private const val PERCENT = 100.0

    fun aggregateCharge(
        id: String,
        samples: List<BatterySampleEntity>,
        endedAt: Long?,
        designCapacityMah: Double?,
        minLevelDelta: Int,
    ): ChargeSessionEntity? {
        if (samples.isEmpty()) return null
        val first = samples.first()
        val last = samples.last()

        var screenOnMs = 0L
        var screenOffMs = 0L
        var integratedMah = 0.0
        var currentSum = 0.0
        var currentCount = 0
        var maxCurrent = 0.0
        var tempSum = 0.0
        var maxTemp = Float.MIN_VALUE

        samples.forEach { sample ->
            if (sample.currentMa > 0) {
                currentSum += sample.currentMa
                currentCount++
                if (sample.currentMa > maxCurrent) maxCurrent = sample.currentMa
            }
            tempSum += sample.temperatureC
            if (sample.temperatureC > maxTemp) maxTemp = sample.temperatureC
        }

        samples.zipWithNext { a, b ->
            val gap = (b.timestamp - a.timestamp).coerceIn(0L, MAX_GAP_MS)
            if (a.screenOn) screenOnMs += gap else screenOffMs += gap
            integratedMah += trapezoidMah(a.currentMa, b.currentMa, gap)
        }

        val chargedMah = counterDeltaMah(first, last, charging = true) ?: integratedMah
        val levelGained = last.level - first.level
        val estimatedCapacity = if (levelGained >= minLevelDelta && chargedMah > 0) {
            chargedMah / (levelGained / PERCENT)
        } else {
            null
        }

        return ChargeSessionEntity(
            id = id,
            startedAt = first.timestamp,
            endedAt = endedAt,
            startLevel = first.level,
            endLevel = last.level,
            plugged = last.plugged.takeIf { it != 0 } ?: first.plugged,
            chargedMah = chargedMah,
            estimatedCapacityMah = estimatedCapacity,
            healthPercent = healthPercent(estimatedCapacity, designCapacityMah),
            avgCurrentMa = if (currentCount > 0) currentSum / currentCount else 0.0,
            maxCurrentMa = maxCurrent,
            avgTemperatureC = tempSum / samples.size,
            maxTemperatureC = if (maxTemp == Float.MIN_VALUE) 0.0 else maxTemp.toDouble(),
            screenOnMs = screenOnMs,
            screenOffMs = screenOffMs,
            durationMs = last.timestamp - first.timestamp,
            sampleCount = samples.size,
        )
    }

    @Suppress("LongMethod")
    fun aggregateDischarge(
        id: String,
        samples: List<BatterySampleEntity>,
        endedAt: Long?,
    ): DischargeSessionEntity? {
        if (samples.isEmpty()) return null
        val first = samples.first()
        val last = samples.last()

        var screenOnMs = 0L
        var screenOffMs = 0L
        var screenOnDrain = 0.0
        var screenOffDrain = 0.0
        var screenOnMah = 0.0
        var screenOffMah = 0.0
        var deepSleepMs = 0L
        var awakeMs = 0L
        var tempSum = 0.0
        var maxTemp = Float.MIN_VALUE

        samples.forEach { sample ->
            tempSum += sample.temperatureC
            if (sample.temperatureC > maxTemp) maxTemp = sample.temperatureC
        }

        samples.zipWithNext { a, b ->
            val gap = (b.timestamp - a.timestamp).coerceIn(0L, MAX_GAP_MS)
            val levelDrop = (a.level - b.level).coerceAtLeast(0).toDouble()
            val segmentMah = trapezoidMah(abs(a.currentMa), abs(b.currentMa), gap)

            if (a.screenOn) {
                screenOnMs += gap
                screenOnDrain += levelDrop
                screenOnMah += segmentMah
            } else {
                screenOffMs += gap
                screenOffDrain += levelDrop
                screenOffMah += segmentMah
            }

            // Monotonic clocks reset on reboot; a negative delta means we crossed one.
            val elapsedDelta = b.elapsedRealtimeMs - a.elapsedRealtimeMs
            val uptimeDelta = b.uptimeMs - a.uptimeMs
            if (elapsedDelta >= 0 && uptimeDelta >= 0 && elapsedDelta <= MAX_GAP_MS) {
                deepSleepMs += (elapsedDelta - uptimeDelta).coerceAtLeast(0L)
                awakeMs += uptimeDelta
            }
        }

        // When a coulomb counter exists it beats integrating a noisy current.
        val counterMah = counterDeltaMah(first, last, charging = false)
        val integrated = screenOnMah + screenOffMah
        if (counterMah != null && integrated > 0) {
            val scale = counterMah / integrated
            screenOnMah *= scale
            screenOffMah *= scale
        }

        return DischargeSessionEntity(
            id = id,
            startedAt = first.timestamp,
            endedAt = endedAt,
            startLevel = first.level,
            endLevel = last.level,
            screenOnMs = screenOnMs,
            screenOffMs = screenOffMs,
            screenOnDrainPercent = screenOnDrain,
            screenOffDrainPercent = screenOffDrain,
            screenOnMah = screenOnMah,
            screenOffMah = screenOffMah,
            avgScreenOnCurrentMa = averageCurrent(screenOnMah, screenOnMs),
            avgScreenOffCurrentMa = averageCurrent(screenOffMah, screenOffMs),
            deepSleepMs = deepSleepMs,
            awakeMs = awakeMs,
            avgTemperatureC = tempSum / samples.size,
            maxTemperatureC = if (maxTemp == Float.MIN_VALUE) 0.0 else maxTemp.toDouble(),
            durationMs = last.timestamp - first.timestamp,
            sampleCount = samples.size,
        )
    }

    /**
     * mAh moved between two samples according to the coulomb counter, or null
     * when the device has none / the reading went the wrong way.
     */
    private fun counterDeltaMah(
        first: BatterySampleEntity,
        last: BatterySampleEntity,
        charging: Boolean,
    ): Double? {
        val start = first.chargeCounterUah ?: return null
        val end = last.chargeCounterUah ?: return null
        val deltaUah = if (charging) end - start else start - end
        return (deltaUah / MICRO_AH_PER_MAH).takeIf { it > 0 }
    }

    private fun trapezoidMah(currentA: Double, currentB: Double, gapMs: Long): Double {
        if (gapMs <= 0) return 0.0
        val avgMa = (currentA + currentB) / 2.0
        if (avgMa <= 0) return 0.0
        return avgMa * (gapMs / HOUR_MS)
    }

    private fun averageCurrent(mah: Double, durationMs: Long): Double =
        if (durationMs <= 0) 0.0 else mah / (durationMs / HOUR_MS)

    private fun healthPercent(estimatedMah: Double?, designMah: Double?): Double? {
        if (estimatedMah == null || designMah == null || designMah <= 0) return null
        return (estimatedMah / designMah) * PERCENT
    }
}
