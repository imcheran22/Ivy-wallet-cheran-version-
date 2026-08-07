package com.ivy.battery.ui

import androidx.compose.runtime.Immutable
import com.ivy.battery.data.BatterySettings
import com.ivy.battery.data.CurrentUnitMode
import com.ivy.battery.domain.model.AppDrain
import com.ivy.battery.domain.model.BatterySnapshot
import com.ivy.battery.domain.model.ChargeSession
import com.ivy.battery.domain.model.DischargeSession
import com.ivy.battery.domain.model.HealthEstimate
import com.ivy.battery.domain.model.TimeEstimates
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.util.concurrent.TimeUnit

enum class HistoryRange(val label: String, val durationMs: Long) {
    DAY("24 hours", TimeUnit.HOURS.toMillis(24)),
    WEEK("7 days", TimeUnit.DAYS.toMillis(7)),
    MONTH("30 days", TimeUnit.DAYS.toMillis(30)),
}

@Immutable
data class HistoryPoint(
    val timestamp: Long,
    val value: Float,
    val charging: Boolean,
)

@Immutable
data class BatteryUiState(
    val snapshot: BatterySnapshot?,
    val estimates: TimeEstimates,
    val health: HealthEstimate?,
    val ongoingCharge: ChargeSession?,
    val ongoingDischarge: DischargeSession?,
    val chargeSessions: ImmutableList<ChargeSession>,
    val dischargeSessions: ImmutableList<DischargeSession>,
    val levelHistory: ImmutableList<HistoryPoint>,
    val temperatureHistory: ImmutableList<HistoryPoint>,
    val currentHistory: ImmutableList<HistoryPoint>,
    val appDrain: ImmutableList<AppDrain>,
    val hasUsageAccess: Boolean,
    val settings: BatterySettings,
    val historyRange: HistoryRange,
    val sampleCount: Int,
    val systemDesignCapacityMah: Double?,
) {
    companion object {
        val Empty = BatteryUiState(
            snapshot = null,
            estimates = TimeEstimates(null, null, null, null, null),
            health = null,
            ongoingCharge = null,
            ongoingDischarge = null,
            chargeSessions = persistentListOf(),
            dischargeSessions = persistentListOf(),
            levelHistory = persistentListOf(),
            temperatureHistory = persistentListOf(),
            currentHistory = persistentListOf(),
            appDrain = persistentListOf(),
            hasUsageAccess = false,
            settings = BatterySettings.Default,
            historyRange = HistoryRange.DAY,
            sampleCount = 0,
            systemDesignCapacityMah = null,
        )
    }
}

sealed interface BatteryUiEvent {
    data class SetMonitoringEnabled(val enabled: Boolean) : BatteryUiEvent
    data class SetDetailedNotification(val enabled: Boolean) : BatteryUiEvent
    data class SetSamplingInterval(val seconds: Int) : BatteryUiEvent

    data class SetChargeAlarmEnabled(val enabled: Boolean) : BatteryUiEvent
    data class SetChargeAlarmLevel(val level: Int) : BatteryUiEvent
    data class SetChargeAlarmSound(val enabled: Boolean) : BatteryUiEvent
    data class SetChargeAlarmVibrate(val enabled: Boolean) : BatteryUiEvent

    data class SetLowBatteryAlarmEnabled(val enabled: Boolean) : BatteryUiEvent
    data class SetLowBatteryLevel(val level: Int) : BatteryUiEvent

    data class SetTemperatureAlarmEnabled(val enabled: Boolean) : BatteryUiEvent
    data class SetTemperatureAlarmC(val celsius: Int) : BatteryUiEvent

    data class SetDesignCapacity(val mah: Int) : BatteryUiEvent
    data class SetTemperatureUnit(val fahrenheit: Boolean) : BatteryUiEvent
    data class SetCurrentUnitMode(val mode: CurrentUnitMode) : BatteryUiEvent
    data class SetInvertCurrentSign(val enabled: Boolean) : BatteryUiEvent
    data class SetTrackAppDrain(val enabled: Boolean) : BatteryUiEvent
    data class SetRetentionDays(val days: Int) : BatteryUiEvent
    data class SetMinLevelDelta(val delta: Int) : BatteryUiEvent

    data class SelectHistoryRange(val range: HistoryRange) : BatteryUiEvent

    data object RequestUsageAccess : BatteryUiEvent
    data object RefreshAppDrain : BatteryUiEvent
    data object ExportCsv : BatteryUiEvent
    data object ClearAllData : BatteryUiEvent
    data object OpenBatteryOptimizationSettings : BatteryUiEvent
}
