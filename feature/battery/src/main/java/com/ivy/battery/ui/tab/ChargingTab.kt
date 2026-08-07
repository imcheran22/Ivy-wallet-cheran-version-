package com.ivy.battery.ui.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivy.battery.domain.model.ChargeSession
import com.ivy.battery.ui.BatteryUiEvent
import com.ivy.battery.ui.BatteryUiState
import com.ivy.battery.ui.component.BatterySection
import com.ivy.battery.ui.component.EmptyState
import com.ivy.battery.ui.component.InfoNote
import com.ivy.battery.ui.component.MetricRow
import com.ivy.battery.ui.component.SettingSliderRow
import com.ivy.battery.ui.component.SettingSwitchRow
import com.ivy.battery.ui.component.StatTile
import com.ivy.battery.ui.component.StatTileRow
import com.ivy.battery.ui.format.formatCurrent
import com.ivy.battery.ui.format.formatDateTime
import com.ivy.battery.ui.format.formatDuration
import com.ivy.battery.ui.format.formatMah
import com.ivy.battery.ui.format.formatPercent
import com.ivy.battery.ui.format.formatTemperature

@Composable
fun ChargingTab(
    state: BatteryUiState,
    onEvent: (BatteryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    val finishedSessions = state.chargeSessions.filter { !it.ongoing }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            val ongoing = state.ongoingCharge
            BatterySection(
                title = "This charge",
                subtitle = if (ongoing == null) {
                    "Plug in a charger to start a measurement"
                } else {
                    "Started ${formatDateTime(ongoing.startedAt)}"
                },
            ) {
                if (ongoing == null) {
                    EmptyState(
                        title = "Not charging",
                        message = "Every charge is measured automatically. " +
                                "Charging from a low level through a wide range gives " +
                                "the most accurate capacity reading.",
                    )
                } else {
                    StatTileRow(
                        left = {
                            StatTile(
                                label = "Charge speed",
                                value = state.snapshot
                                    ?.let { formatCurrent(it.currentMa) }
                                    ?: formatCurrent(ongoing.avgCurrentMa),
                                icon = Icons.Rounded.Bolt,
                                accent = MaterialTheme.colorScheme.primary,
                                caption = "avg ${formatCurrent(ongoing.avgCurrentMa)}",
                            )
                        },
                        right = {
                            StatTile(
                                label = "Gained",
                                value = "+${ongoing.levelGained}%",
                                icon = Icons.Rounded.BatteryChargingFull,
                                caption = "${ongoing.startLevel}% → ${ongoing.endLevel}%",
                            )
                        },
                    )
                    StatTileRow(
                        left = {
                            StatTile(
                                label = "Energy in",
                                value = formatMah(ongoing.chargedMah),
                            )
                        },
                        right = {
                            StatTile(
                                label = "Elapsed",
                                value = formatDuration(ongoing.durationMs),
                                icon = Icons.Rounded.Timer,
                            )
                        },
                    )
                    StatTileRow(
                        left = {
                            StatTile(
                                label = "Temperature",
                                value = formatTemperature(
                                    ongoing.avgTemperatureC.toFloat(),
                                    settings.temperatureInFahrenheit,
                                ),
                                icon = Icons.Rounded.Thermostat,
                                caption = "peak " + formatTemperature(
                                    ongoing.maxTemperatureC.toFloat(),
                                    settings.temperatureInFahrenheit,
                                ),
                            )
                        },
                        right = {
                            StatTile(
                                label = "Speed",
                                value = "%.1f%%/h".format(ongoing.speedPercentPerHour),
                            )
                        },
                    )
                }
            }
        }

        item {
            BatterySection(
                title = "Charge alarm",
                subtitle = "Stop charging before the battery is stressed",
            ) {
                SettingSwitchRow(
                    title = "Alarm when charged",
                    description = "Sounds an alarm once the level reaches your target",
                    checked = settings.chargeAlarmEnabled,
                    onCheckedChange = { onEvent(BatteryUiEvent.SetChargeAlarmEnabled(it)) },
                )
                SettingSliderRow(
                    title = "Target level",
                    valueLabel = "${settings.chargeAlarmLevel}%",
                    description = "Keeping a lithium-ion cell between roughly 20% and 80% " +
                            "is what makes it last; 100% every night is what wears it out.",
                    value = settings.chargeAlarmLevel.toFloat(),
                    range = ALARM_MIN..ALARM_MAX,
                    steps = ALARM_STEPS,
                    onValueChange = {
                        onEvent(BatteryUiEvent.SetChargeAlarmLevel(it.toInt()))
                    },
                )
                SettingSwitchRow(
                    title = "Play sound",
                    checked = settings.chargeAlarmSound,
                    onCheckedChange = { onEvent(BatteryUiEvent.SetChargeAlarmSound(it)) },
                )
                SettingSwitchRow(
                    title = "Vibrate",
                    checked = settings.chargeAlarmVibrate,
                    onCheckedChange = { onEvent(BatteryUiEvent.SetChargeAlarmVibrate(it)) },
                )
            }
        }

        item {
            BatterySection(
                title = "Measured charges",
                subtitle = "${finishedSessions.size} completed sessions",
            ) {
                if (finishedSessions.isEmpty()) {
                    EmptyState(
                        title = "No completed charges yet",
                        message = "Finish a charge and its full breakdown will appear here.",
                    )
                } else {
                    InfoNote(
                        text = "A session only produces a capacity estimate when it covers " +
                                "at least ${settings.minLevelDeltaForCapacity}% of the range - " +
                                "short top-ups are too noisy to measure."
                    )
                }
            }
        }

        items(finishedSessions, key = { it.id }) { session ->
            ChargeSessionCard(session = session, fahrenheit = settings.temperatureInFahrenheit)
        }
    }
}

@Composable
private fun ChargeSessionCard(
    session: ChargeSession,
    fahrenheit: Boolean,
    modifier: Modifier = Modifier,
) {
    BatterySection(
        title = formatDateTime(session.startedAt),
        subtitle = "${session.startLevel}% → ${session.endLevel}% · " +
                "${session.plugType.label} · ${formatDuration(session.durationMs)}",
        modifier = modifier,
    ) {
        MetricRow("Energy delivered", formatMah(session.chargedMah))
        MetricRow(
            label = "Capacity measured",
            value = session.estimatedCapacityMah?.let { formatMah(it) }
                ?: "range too small",
        )
        MetricRow(
            label = "Health from this charge",
            value = session.healthPercent?.let { formatPercent(it, 0) } ?: "—",
        )
        MetricRow("Average speed", formatCurrent(session.avgCurrentMa))
        MetricRow("Peak speed", formatCurrent(session.maxCurrentMa))
        MetricRow(
            "Temperature",
            "${formatTemperature(session.avgTemperatureC.toFloat(), fahrenheit)} avg · " +
                    "${formatTemperature(session.maxTemperatureC.toFloat(), fahrenheit)} peak",
        )
        MetricRow("Screen on while charging", formatDuration(session.screenOnMs))
        MetricRow("Samples", session.sampleCount.toString())
    }
}

private const val ALARM_MIN = 30f
private const val ALARM_MAX = 100f
private const val ALARM_STEPS = 13
