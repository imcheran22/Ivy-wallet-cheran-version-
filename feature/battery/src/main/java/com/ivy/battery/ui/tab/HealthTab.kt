package com.ivy.battery.ui.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivy.battery.ui.BatteryUiEvent
import com.ivy.battery.ui.BatteryUiState
import com.ivy.battery.ui.HistoryPoint
import com.ivy.battery.ui.component.BatterySection
import com.ivy.battery.ui.component.EmptyState
import com.ivy.battery.ui.component.HistoryChart
import com.ivy.battery.ui.component.InfoNote
import com.ivy.battery.ui.component.MetricRow
import com.ivy.battery.ui.component.SettingSliderRow
import com.ivy.battery.ui.component.ShareBar
import com.ivy.battery.ui.component.StatTile
import com.ivy.battery.ui.component.StatTileRow
import com.ivy.battery.ui.format.formatDateTime
import com.ivy.battery.ui.format.formatMah
import com.ivy.battery.ui.format.formatPercent
import kotlin.math.roundToInt

@Composable
fun HealthTab(
    state: BatteryUiState,
    onEvent: (BatteryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val health = state.health

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            BatterySection(
                title = "Battery health",
                subtitle = health?.lastMeasuredAt
                    ?.let { "Last measured ${formatDateTime(it)}" }
                    ?: "Waiting for the first usable charge",
            ) {
                if (health?.healthPercent == null) {
                    EmptyState(
                        title = "Not measured yet",
                        message = "Charge the phone across a wide range " +
                                "(${state.settings.minLevelDeltaForCapacity}% or more) " +
                                "and the health estimate will appear here.",
                    )
                } else {
                    StatTileRow(
                        left = {
                            StatTile(
                                label = "Health",
                                value = formatPercent(health.healthPercent, 0),
                                icon = Icons.Rounded.Favorite,
                                accent = healthColor(health.healthPercent),
                                caption = "of design capacity",
                            )
                        },
                        right = {
                            StatTile(
                                label = "Wear",
                                value = health.wearPercent
                                    ?.let { formatPercent(it, 0) } ?: "—",
                                caption = "capacity lost",
                            )
                        },
                    )
                    ShareBar(
                        fraction = (health.healthPercent / 100.0).toFloat(),
                        color = healthColor(health.healthPercent),
                    )
                }

                MetricRow(
                    "Measured capacity",
                    health?.estimatedCapacityMah?.let { formatMah(it) } ?: "—",
                )
                MetricRow(
                    "Design capacity",
                    health?.designCapacityMah?.let { formatMah(it) } ?: "unknown",
                )
                MetricRow(
                    "Best measurement",
                    health?.bestMeasurementMah?.let { formatMah(it) } ?: "—",
                )
                MetricRow(
                    "Lowest measurement",
                    health?.worstMeasurementMah?.let { formatMah(it) } ?: "—",
                )
                MetricRow("Measurements used", (health?.measurementCount ?: 0).toString())
            }
        }

        item {
            BatterySection(title = "Charge cycles") {
                StatTileRow(
                    left = {
                        StatTile(
                            label = "Full-equivalent cycles",
                            value = "%.1f".format(health?.chargeCycles ?: 0.0),
                            icon = Icons.Rounded.Loop,
                            caption = "since monitoring started",
                        )
                    },
                    right = {
                        StatTile(
                            label = "Charges recorded",
                            value = state.chargeSessions.size.toString(),
                        )
                    },
                )
                InfoNote(
                    text = "A cycle is 100% of charge added, however it was accumulated - " +
                            "five 20% top-ups count as one cycle, not five."
                )
            }
        }

        item {
            val capacityPoints = state.chargeSessions
                .filter { it.estimatedCapacityMah != null }
                .sortedBy { it.startedAt }
                .map {
                    HistoryPoint(
                        timestamp = it.endedAt ?: it.startedAt,
                        value = it.estimatedCapacityMah!!.toFloat(),
                        charging = false,
                    )
                }

            BatterySection(
                title = "Capacity over time",
                subtitle = "Every usable measurement, oldest first",
            ) {
                HistoryChart(
                    points = capacityPoints,
                    lineColor = MaterialTheme.colorScheme.primary,
                    chargingColor = MaterialTheme.colorScheme.tertiary,
                    highlightCharging = false,
                    valueFormatter = { "${it.roundToInt()}" },
                )
            }
        }

        item {
            BatterySection(
                title = "Calibration",
                subtitle = "Only touch these if the numbers look wrong for your device",
            ) {
                val detected = state.systemDesignCapacityMah
                MetricRow(
                    "Detected design capacity",
                    detected?.let { formatMah(it) } ?: "could not be read",
                )
                SettingSliderRow(
                    title = "Override design capacity",
                    valueLabel = state.settings.designCapacityOverrideMah
                        .takeIf { it > 0 }
                        ?.let { "$it mAh" }
                        ?: "auto",
                    description = "Set this to the mAh printed on your battery's spec " +
                            "sheet when the automatic reading is missing or obviously wrong.",
                    value = state.settings.designCapacityOverrideMah.toFloat(),
                    range = 0f..CAPACITY_MAX,
                    steps = CAPACITY_STEPS,
                    onValueChange = {
                        onEvent(BatteryUiEvent.SetDesignCapacity(it.roundToInt()))
                    },
                )
                SettingSliderRow(
                    title = "Minimum range per measurement",
                    valueLabel = "${state.settings.minLevelDeltaForCapacity}%",
                    description = "Charges covering less than this are recorded but not " +
                            "used for the health estimate. Higher is more accurate but " +
                            "produces fewer measurements.",
                    value = state.settings.minLevelDeltaForCapacity.toFloat(),
                    range = DELTA_MIN..DELTA_MAX,
                    steps = DELTA_STEPS,
                    onValueChange = { onEvent(BatteryUiEvent.SetMinLevelDelta(it.roundToInt())) },
                )
            }
        }

        item {
            InfoNote(
                text = "Android exposes no direct health reading, so this is measured the " +
                        "only way it can be: by counting the charge that actually goes into " +
                        "the battery and comparing it with the percentage gained. It takes " +
                        "several charges before the number settles down."
            )
        }
    }
}

@Composable
private fun healthColor(healthPercent: Double): androidx.compose.ui.graphics.Color = when {
    healthPercent >= GOOD_HEALTH -> MaterialTheme.colorScheme.primary
    healthPercent >= FAIR_HEALTH -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

private const val GOOD_HEALTH = 85.0
private const val FAIR_HEALTH = 70.0
private const val CAPACITY_MAX = 10_000f
private const val CAPACITY_STEPS = 99
private const val DELTA_MIN = 5f
private const val DELTA_MAX = 60f
private const val DELTA_STEPS = 10
