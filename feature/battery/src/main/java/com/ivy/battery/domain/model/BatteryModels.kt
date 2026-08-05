package com.ivy.battery.domain.model

import android.os.BatteryManager

/**
 * How the device is connected to power.
 *
 * [android.os.BatteryManager.BATTERY_PLUGGED_DOCK] is API 33+, so its raw value
 * is used to keep this usable on the project's min SDK.
 */
enum class PlugType(val label: String) {
    NONE("Unplugged"),
    AC("AC charger"),
    USB("USB"),
    WIRELESS("Wireless"),
    DOCK("Dock"),
    UNKNOWN("Unknown");

    companion object {
        private const val PLUGGED_DOCK = 8

        fun from(plugged: Int): PlugType = when (plugged) {
            0 -> NONE
            BatteryManager.BATTERY_PLUGGED_AC -> AC
            BatteryManager.BATTERY_PLUGGED_USB -> USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> WIRELESS
            PLUGGED_DOCK -> DOCK
            else -> UNKNOWN
        }
    }
}

enum class ChargeStatus(val label: String) {
    CHARGING("Charging"),
    DISCHARGING("Discharging"),
    FULL("Full"),
    NOT_CHARGING("Not charging"),
    UNKNOWN("Unknown");

    companion object {
        fun from(status: Int): ChargeStatus = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> CHARGING
            BatteryManager.BATTERY_STATUS_DISCHARGING -> DISCHARGING
            BatteryManager.BATTERY_STATUS_FULL -> FULL
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> NOT_CHARGING
            else -> UNKNOWN
        }
    }
}

/**
 * The health value the *firmware* reports. It is coarse (most devices only ever
 * say "Good"), which is exactly why we also estimate health from measured
 * charge sessions - see [com.ivy.battery.domain.HealthCalculator].
 */
enum class ReportedHealth(val label: String) {
    GOOD("Good"),
    OVERHEAT("Overheat"),
    DEAD("Dead"),
    OVER_VOLTAGE("Over voltage"),
    COLD("Cold"),
    FAILURE("Unspecified failure"),
    UNKNOWN("Unknown");

    companion object {
        fun from(health: Int): ReportedHealth = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> GOOD
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> OVERHEAT
            BatteryManager.BATTERY_HEALTH_DEAD -> DEAD
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> OVER_VOLTAGE
            BatteryManager.BATTERY_HEALTH_COLD -> COLD
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> FAILURE
            else -> UNKNOWN
        }
    }
}

/**
 * A single point-in-time reading of every battery metric the platform exposes.
 */
data class BatterySnapshot(
    val timestamp: Long,
    val level: Int,
    val scale: Int,
    val status: ChargeStatus,
    val plugType: PlugType,
    val reportedHealth: ReportedHealth,
    val technology: String,
    val temperatureC: Float,
    val voltageMv: Int,
    /** Positive while charging, negative while discharging. */
    val currentMa: Double,
    val averageCurrentMa: Double?,
    /** Remaining charge in the battery, in µAh. Null when the device doesn't report it. */
    val chargeCounterUah: Int?,
    val energyCounterNwh: Long?,
    val present: Boolean,
    val screenOn: Boolean,
    val powerSaveMode: Boolean,
    /** Milliseconds the CPU has been awake since boot. */
    val uptimeMs: Long,
    /** Milliseconds since boot, including deep sleep. */
    val elapsedRealtimeMs: Long,
) {
    val charging: Boolean
        get() = plugType != PlugType.NONE &&
                (status == ChargeStatus.CHARGING || status == ChargeStatus.FULL)

    /** Instantaneous power flow in watts (positive = into the battery). */
    val powerWatts: Double
        get() = (voltageMv / 1000.0) * (currentMa / 1000.0)

    val temperatureF: Float
        get() = temperatureC * 9f / 5f + 32f

    val deepSleepMs: Long
        get() = (elapsedRealtimeMs - uptimeMs).coerceAtLeast(0L)

    val remainingCapacityMah: Double?
        get() = chargeCounterUah?.let { it / 1000.0 }
}

/**
 * A finished (or in-flight) charging measurement.
 */
data class ChargeSession(
    val id: String,
    val startedAt: Long,
    val endedAt: Long?,
    val startLevel: Int,
    val endLevel: Int,
    val plugType: PlugType,
    /** mAh that actually went into the battery over the session. */
    val chargedMah: Double,
    /** Full-battery capacity extrapolated from [chargedMah] and the % gained. */
    val estimatedCapacityMah: Double?,
    val healthPercent: Double?,
    val avgCurrentMa: Double,
    val maxCurrentMa: Double,
    val avgTemperatureC: Double,
    val maxTemperatureC: Double,
    val screenOnMs: Long,
    val screenOffMs: Long,
    val durationMs: Long,
    val sampleCount: Int,
) {
    val levelGained: Int get() = endLevel - startLevel
    val ongoing: Boolean get() = endedAt == null

    /** Average % gained per hour. */
    val speedPercentPerHour: Double
        get() = if (durationMs <= 0) 0.0 else levelGained * HOUR_MS / durationMs.toDouble()
}

/**
 * A finished (or in-flight) discharge measurement, split by screen state -
 * the split is what makes the numbers actionable.
 */
data class DischargeSession(
    val id: String,
    val startedAt: Long,
    val endedAt: Long?,
    val startLevel: Int,
    val endLevel: Int,
    val screenOnMs: Long,
    val screenOffMs: Long,
    val screenOnDrainPercent: Double,
    val screenOffDrainPercent: Double,
    val screenOnMah: Double,
    val screenOffMah: Double,
    val avgScreenOnCurrentMa: Double,
    val avgScreenOffCurrentMa: Double,
    val deepSleepMs: Long,
    val awakeMs: Long,
    val avgTemperatureC: Double,
    val maxTemperatureC: Double,
    val durationMs: Long,
    val sampleCount: Int,
) {
    val levelDrained: Int get() = startLevel - endLevel
    val ongoing: Boolean get() = endedAt == null

    /** % drained per hour of screen-on time. */
    val screenOnDrainPerHour: Double
        get() = if (screenOnMs <= 0) 0.0 else screenOnDrainPercent * HOUR_MS / screenOnMs.toDouble()

    /** % drained per hour of screen-off time. */
    val screenOffDrainPerHour: Double
        get() = if (screenOffMs <= 0) 0.0 else screenOffDrainPercent * HOUR_MS / screenOffMs.toDouble()

    val deepSleepRatio: Double
        get() = if (screenOffMs <= 0) 0.0 else (deepSleepMs.toDouble() / screenOffMs).coerceIn(0.0, 1.0)
}

/**
 * Aggregated battery wear, derived from every usable charge measurement.
 */
data class HealthEstimate(
    val designCapacityMah: Double?,
    val estimatedCapacityMah: Double?,
    val healthPercent: Double?,
    val measurementCount: Int,
    /** Full-equivalent cycles: the sum of all charged % divided by 100. */
    val chargeCycles: Double,
    val bestMeasurementMah: Double?,
    val worstMeasurementMah: Double?,
    val lastMeasuredAt: Long?,
) {
    val wearPercent: Double?
        get() = healthPercent?.let { (100.0 - it).coerceAtLeast(0.0) }
}

/**
 * A single app's share of a discharge session.
 */
data class AppDrain(
    val packageName: String,
    val appLabel: String,
    val foregroundMs: Long,
    val estimatedMah: Double,
    val estimatedPercent: Double,
)

/**
 * How much runtime is left, computed separately for the two usage modes so the
 * numbers stay honest instead of averaging a mixed session into mush.
 */
data class TimeEstimates(
    val timeToFullMs: Long?,
    val timeToTargetMs: Long?,
    val screenOnRemainingMs: Long?,
    val screenOffRemainingMs: Long?,
    val mixedRemainingMs: Long?,
)

const val HOUR_MS: Long = 60L * 60L * 1000L
