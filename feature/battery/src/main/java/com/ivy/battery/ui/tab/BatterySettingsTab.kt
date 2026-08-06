package com.ivy.battery.ui.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivy.battery.data.CurrentUnitMode
import com.ivy.battery.ui.BatteryUiEvent
import com.ivy.battery.ui.BatteryUiState
import com.ivy.battery.ui.component.BatterySection
import com.ivy.battery.ui.component.InfoNote
import com.ivy.battery.ui.component.MetricRow
import com.ivy.battery.ui.component.SettingSliderRow
import com.ivy.battery.ui.component.SettingSwitchRow
import com.ivy.battery.ui.format.formatCurrent
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatterySettingsTab(
    state: BatteryUiState,
    onEvent: (BatteryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            BatterySection(title = "Monitoring") {
                SettingSwitchRow(
                    title = "Measure my battery",
                    description = "Samples in the background so charge and discharge " +
                            "sessions are complete even with the screen off",
                    checked = settings.monitoringEnabled,
                    onCheckedChange = { onEvent(BatteryUiEvent.SetMonitoringEnabled(it)) },
                )
                SettingSwitchRow(
                    title = "Live stats in the notification",
                    description = "Android requires a notification for background " +
                            "monitoring; this decides how much it shows",
                    checked = settings.persistentNotification,
                    onCheckedChange = { onEvent(BatteryUiEvent.SetDetailedNotification(it)) },
                )
                SettingSliderRow(
                    title = "Sampling interval",
                    valueLabel = "${settings.samplingIntervalSec} s",
                    description = "Shorter means finer measurements and slightly more " +
                            "overhead. Readings are also taken whenever the system " +
                            "reports a change, whatever this is set to.",
                    value = settings.samplingIntervalSec.toFloat(),
                    range = INTERVAL_MIN..INTERVAL_MAX,
                    steps = INTERVAL_STEPS,
                    onValueChange = {
                        onEvent(BatteryUiEvent.SetSamplingInterval(it.roundToInt()))
                    },
                )
                FilledTonalButton(
                    onClick = { onEvent(BatteryUiEvent.OpenBatteryOptimizationSettings) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open app info / battery optimisation")
                }
                InfoNote(
                    text = "If sessions keep getting cut short, exempt Ivy Wallet from " +
                            "battery optimisation. Some manufacturers kill background " +
                            "services aggressively regardless of what Android allows."
                )
            }
        }

        item {
            BatterySection(title = "Overheating alarm") {
                SettingSwitchRow(
                    title = "Warn me when hot",
                    description = "Heat degrades a battery faster than almost anything else",
                    checked = settings.temperatureAlarmEnabled,
                    onCheckedChange = { onEvent(BatteryUiEvent.SetTemperatureAlarmEnabled(it)) },
                )
                SettingSliderRow(
                    title = "Alarm above",
                    valueLabel = "${settings.temperatureAlarmC}°C",
                    value = settings.temperatureAlarmC.toFloat(),
                    range = TEMP_MIN..TEMP_MAX,
                    steps = TEMP_STEPS,
                    onValueChange = {
                        onEvent(BatteryUiEvent.SetTemperatureAlarmC(it.roundToInt()))
                    },
                )
            }
        }

        item {
            BatterySection(title = "Units") {
                SettingSwitchRow(
                    title = "Show temperature in °F",
                    checked = settings.temperatureInFahrenheit,
                    onCheckedChange = { onEvent(BatteryUiEvent.SetTemperatureUnit(it)) },
                )
                Text(
                    text = "Current sensor unit",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "Android documents microamps, but plenty of manufacturers " +
                            "report milliamps instead. Pin it here if the live current " +
                            "reads 1000× too high or too low.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    CurrentUnitMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = settings.currentUnitMode == mode,
                            onClick = { onEvent(BatteryUiEvent.SetCurrentUnitMode(mode)) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = CurrentUnitMode.entries.size,
                            ),
                        ) {
                            Text(mode.displayName())
                        }
                    }
                }
                SettingSwitchRow(
                    title = "Invert current sign",
                    description = "Turn on if charging shows as negative on your device",
                    checked = settings.invertCurrentSign,
                    onCheckedChange = { onEvent(BatteryUiEvent.SetInvertCurrentSign(it)) },
                )
                MetricRow(
                    "Live reading",
                    state.snapshot?.let { formatCurrent(it.currentMa) } ?: "—",
                )
            }
        }

        item {
            BatterySection(title = "Data") {
                SettingSliderRow(
                    title = "Keep history for",
                    valueLabel = "${settings.retentionDays} days",
                    description = "Older samples and sessions are deleted automatically",
                    value = settings.retentionDays.toFloat(),
                    range = RETENTION_MIN..RETENTION_MAX,
                    steps = RETENTION_STEPS,
                    onValueChange = { onEvent(BatteryUiEvent.SetRetentionDays(it.roundToInt())) },
                )
                MetricRow("Samples stored", state.sampleCount.toString())
            }
        }
    }
}

private fun CurrentUnitMode.displayName(): String = when (this) {
    CurrentUnitMode.AUTO -> "Auto"
    CurrentUnitMode.MICRO_AMPS -> "µA"
    CurrentUnitMode.MILLI_AMPS -> "mA"
}

private const val INTERVAL_MIN = 5f
private const val INTERVAL_MAX = 120f
private const val INTERVAL_STEPS = 22
private const val TEMP_MIN = 35f
private const val TEMP_MAX = 55f
private const val TEMP_STEPS = 19
private const val RETENTION_MIN = 1f
private const val RETENTION_MAX = 180f
private const val RETENTION_STEPS = 11
