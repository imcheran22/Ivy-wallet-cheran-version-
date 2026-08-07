package com.ivy.battery.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One raw telemetry reading. Everything else in the feature is derived from
 * these rows, so they stay deliberately dumb.
 */
@Entity(
    tableName = "battery_samples",
    indices = [Index("timestamp")]
)
data class BatterySampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val level: Int,
    val status: Int,
    val plugged: Int,
    val temperatureC: Float,
    val voltageMv: Int,
    val currentMa: Double,
    val chargeCounterUah: Int?,
    val screenOn: Boolean,
    val uptimeMs: Long,
    val elapsedRealtimeMs: Long,
)

@Entity(
    tableName = "charge_sessions",
    indices = [Index("startedAt")]
)
data class ChargeSessionEntity(
    @PrimaryKey
    val id: String,
    val startedAt: Long,
    val endedAt: Long?,
    val startLevel: Int,
    val endLevel: Int,
    val plugged: Int,
    val chargedMah: Double,
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
)

@Entity(
    tableName = "discharge_sessions",
    indices = [Index("startedAt")]
)
data class DischargeSessionEntity(
    @PrimaryKey
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
)

/**
 * Per-app share of a discharge session's screen-on drain.
 */
@Entity(
    tableName = "app_drain",
    primaryKeys = ["sessionId", "packageName"]
)
data class AppDrainEntity(
    val sessionId: String,
    val packageName: String,
    val foregroundMs: Long,
    val estimatedMah: Double,
    val estimatedPercent: Double,
    val updatedAt: Long,
)
