package com.ivy.battery.ui.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivy.battery.domain.model.BatterySnapshot
import com.ivy.battery.ui.BatteryUiState
import com.ivy.battery.ui.component.BatterySection
import com.ivy.battery.ui.component.BatteryGauge
import com.ivy.battery.ui.component.InfoNote
import com.ivy.battery.ui.component.MetricRow
import com.ivy.battery.ui.component.StatTile
import com.ivy.battery.ui.component.StatTileRow
import com.ivy.battery.ui.format.formatCurrent
import com.ivy.battery.ui.format.formatDuration
import com.ivy.battery.ui.format.formatMah
import com.ivy.battery.ui.format.formatPercent
import com.ivy.battery.ui.format.formatTemperature
import com.ivy.battery.ui.format.formatVoltage
import com.ivy.battery.ui.format.formatWatts
import com.ivy.battery.ui.theme.BatteryLevelColors

@Composable
fun OverviewTab(
    state: BatteryUiState,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.snapshot
    val accent = BatteryLevelColors.forLevel(
        level = snapshot?.level ?: 0,
        charging = snapshot?.charging == true,
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            BatteryGauge(
                level = snapshot?.level ?: 0,
                charging = snapshot?.charging == true,
                accentColor = accent,
                statusLabel = statusLabel(snapshot),
                detailLabel = detailLabel(state),
            )
        }

        item {
            BatterySection(title = "Right now") {
                StatTileRow(
                    left = {
                        StatTile(
                            label = "Current",
                            value = snapshot?.let { formatCurrent(it.currentMa) } ?: "—",
                            icon = Icons.Rounded.Bolt,
                            accent = accent,
                            caption = if (snapshot?.charging == true) "into battery" else "draw",
                        )
                    },
                    right = {
                        StatTile(
                            label = "Power",
                            value = snapshot?.let { formatWatts(it.powerWatts) } ?: "—",
                            icon = Icons.Rounded.Speed,
                        )
                    },
                )
                StatTileRow(
                    left = {
                        StatTile(
                            label = "Temperature",
                            value = snapshot?.let {
                                formatTemperature(
                                    it.temperatureC,
                                    state.settings.temperatureInFahrenheit
                                )
                            } ?: "—",
                            icon = Icons.Rounded.Thermostat,
                            accent = temperatureAccent(snapshot),
                        )
                    },
                    right = {
                        StatTile(
                            label = "Voltage",
                            value = snapshot?.let { formatVoltage(it.voltageMv) } ?: "—",
                        )
                    },
                )
                StatTileRow(
                    left = {
                        StatTile(
                            label = "Charge left",
                            value = snapshot?.remainingCapacityMah?.let { formatMah(it) } ?: "—",
                            caption = "coulomb counter",
                        )
                    },
                    right = {
                        StatTile(
                            label = "Health",
                            value = state.health?.healthPercent?.let { formatPercent(it, 0) }
                                ?: "measuring…",
                            icon = Icons.Rounded.Favorite,
                            accent = healthAccent(state.health?.healthPercent),
                        )
                    },
                )
            }
        }

        item {
            BatterySection(
                title = "Runtime left",
                subtitle = "Predicted from your own measured drain rates",
            ) {
                StatTileRow(
                    left = {
                        StatTile(
                            label = "Screen on",
                            value = state.estimates.screenOnRemainingMs
                                ?.let { formatDuration(it) } ?: "—",
                            icon = Icons.Rounded.LightMode,
                        )
                    },
                    right = {
                        StatTile(
                            label = "Screen off",
                            value = state.estimates.screenOffRemainingMs
                                ?.let { formatDuration(it) } ?: "—",
                            icon = Icons.Rounded.Bedtime,
                        )
                    },
                )
                if (snapshot?.charging == true) {
                    StatTileRow(
                        left = {
                            StatTile(
                                label = "Until full",
                                value = state.estimates.timeToFullMs
                                    ?.let { formatDuration(it) } ?: "—",
                                icon = Icons.Rounded.Timer,
                            )
                        },
                        right = {
                            StatTile(
                                label = "Until ${state.settings.chargeAlarmLevel}%",
                                value = state.estimates.timeToTargetMs
                                    ?.let { formatDuration(it) } ?: "reached",
                                icon = Icons.Rounded.Timer,
                            )
                        },
                    )
                }
            }
        }

        item {
            BatterySection(
                title = "Every reading",
                subtitle = "Raw values straight from the battery driver",
            ) {
                MetricRow("Status", snapshot?.status?.label ?: "—")
                MetricRow("Power source", snapshot?.plugType?.label ?: "—")
                MetricRow("Technology", snapshot?.technology ?: "—")
                MetricRow("Firmware health", snapshot?.reportedHealth?.label ?: "—")
                MetricRow("Battery present", if (snapshot?.present == true) "Yes" else "No")
                MetricRow(
                    "Battery saver",
                    if (snapshot?.powerSaveMode == true) "On" else "Off",
                )
                MetricRow("Screen", if (snapshot?.screenOn == true) "On" else "Off")
                MetricRow(
                    "Average current",
                    snapshot?.averageCurrentMa?.let { formatCurrent(it) } ?: "not reported",
                )
                MetricRow(
                    "Charge counter",
                    snapshot?.chargeCounterUah?.let { "$it µAh" } ?: "not reported",
                )
                MetricRow(
                    "Energy counter",
                    snapshot?.energyCounterNwh?.let { "$it nWh" } ?: "not reported",
                )
                MetricRow(
                    "Design capacity",
                    state.health?.designCapacityMah?.let { formatMah(it) } ?: "unknown",
                )
                MetricRow("Level scale", snapshot?.scale?.toString() ?: "—")
                MetricRow("Awake since boot", snapshot?.let { formatDuration(it.uptimeMs) } ?: "—")
                MetricRow(
                    "Deep sleep since boot",
                    snapshot?.let { formatDuration(it.deepSleepMs) } ?: "—",
                )
                MetricRow("Samples stored", state.sampleCount.toString())
            }
        }

        if (snapshot?.chargeCounterUah == null) {
            item {
                InfoNote(
                    text = "This device doesn't expose a coulomb counter, so capacity is " +
                            "worked out by integrating the reported current instead. " +
                            "Expect a bit more spread between measurements."
                )
            }
        }
    }
}

private fun statusLabel(snapshot: BatterySnapshot?): String = when {
    snapshot == null -> "Reading battery…"
    snapshot.charging -> "Charging · ${snapshot.plugType.label}"
    else -> snapshot.status.label
}

private fun detailLabel(state: BatteryUiState): String? {
    val snapshot = state.snapshot ?: return null
    return when {
        snapshot.charging -> state.estimates.timeToFullMs
            ?.let { "${formatDuration(it)} until full" }
            ?: "Measuring charge speed…"

        else -> state.estimates.mixedRemainingMs
            ?.let { "About ${formatDuration(it)} left at your usual usage" }
            ?: "Measuring drain rate…"
    }
}

@Composable
private fun temperatureAccent(snapshot: BatterySnapshot?): androidx.compose.ui.graphics.Color? =
    when {
        snapshot == null -> null
        snapshot.temperatureC >= HOT_C -> MaterialTheme.colorScheme.error
        else -> null
    }

@Composable
private fun healthAccent(healthPercent: Double?): androidx.compose.ui.graphics.Color? = when {
    healthPercent == null -> null
    healthPercent < POOR_HEALTH_PERCENT -> MaterialTheme.colorScheme.error
    else -> null
}

private const val HOT_C = 40f
private const val POOR_HEALTH_PERCENT = 70.0
