package com.ivy.battery.data

import com.ivy.battery.data.db.BatterySampleEntity
import com.ivy.battery.data.db.ChargeSessionEntity
import com.ivy.battery.data.db.DischargeSessionEntity
import com.ivy.battery.domain.model.BatterySnapshot
import com.ivy.battery.domain.model.ChargeSession
import com.ivy.battery.domain.model.ChargeStatus
import com.ivy.battery.domain.model.DischargeSession
import com.ivy.battery.domain.model.PlugType
import android.os.BatteryManager

fun BatterySnapshot.toEntity(): BatterySampleEntity = BatterySampleEntity(
    timestamp = timestamp,
    level = level,
    status = status.toPlatformStatus(),
    plugged = plugType.toPlatformPlugged(),
    temperatureC = temperatureC,
    voltageMv = voltageMv,
    currentMa = currentMa,
    chargeCounterUah = chargeCounterUah,
    screenOn = screenOn,
    uptimeMs = uptimeMs,
    elapsedRealtimeMs = elapsedRealtimeMs,
)

fun ChargeSessionEntity.toDomain(): ChargeSession = ChargeSession(
    id = id,
    startedAt = startedAt,
    endedAt = endedAt,
    startLevel = startLevel,
    endLevel = endLevel,
    plugType = PlugType.from(plugged),
    chargedMah = chargedMah,
    estimatedCapacityMah = estimatedCapacityMah,
    healthPercent = healthPercent,
    avgCurrentMa = avgCurrentMa,
    maxCurrentMa = maxCurrentMa,
    avgTemperatureC = avgTemperatureC,
    maxTemperatureC = maxTemperatureC,
    screenOnMs = screenOnMs,
    screenOffMs = screenOffMs,
    durationMs = durationMs,
    sampleCount = sampleCount,
)

fun DischargeSessionEntity.toDomain(): DischargeSession = DischargeSession(
    id = id,
    startedAt = startedAt,
    endedAt = endedAt,
    startLevel = startLevel,
    endLevel = endLevel,
    screenOnMs = screenOnMs,
    screenOffMs = screenOffMs,
    screenOnDrainPercent = screenOnDrainPercent,
    screenOffDrainPercent = screenOffDrainPercent,
    screenOnMah = screenOnMah,
    screenOffMah = screenOffMah,
    avgScreenOnCurrentMa = avgScreenOnCurrentMa,
    avgScreenOffCurrentMa = avgScreenOffCurrentMa,
    deepSleepMs = deepSleepMs,
    awakeMs = awakeMs,
    avgTemperatureC = avgTemperatureC,
    maxTemperatureC = maxTemperatureC,
    durationMs = durationMs,
    sampleCount = sampleCount,
)

private fun ChargeStatus.toPlatformStatus(): Int = when (this) {
    ChargeStatus.CHARGING -> BatteryManager.BATTERY_STATUS_CHARGING
    ChargeStatus.DISCHARGING -> BatteryManager.BATTERY_STATUS_DISCHARGING
    ChargeStatus.FULL -> BatteryManager.BATTERY_STATUS_FULL
    ChargeStatus.NOT_CHARGING -> BatteryManager.BATTERY_STATUS_NOT_CHARGING
    ChargeStatus.UNKNOWN -> BatteryManager.BATTERY_STATUS_UNKNOWN
}

private fun PlugType.toPlatformPlugged(): Int = when (this) {
    PlugType.NONE -> 0
    PlugType.AC -> BatteryManager.BATTERY_PLUGGED_AC
    PlugType.USB -> BatteryManager.BATTERY_PLUGGED_USB
    PlugType.WIRELESS -> BatteryManager.BATTERY_PLUGGED_WIRELESS
    PlugType.DOCK -> PLUGGED_DOCK
    PlugType.UNKNOWN -> -1
}

private const val PLUGGED_DOCK = 8
