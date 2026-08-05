package com.ivy.battery.ui.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivy.battery.domain.model.DischargeSession
import com.ivy.battery.ui.BatteryUiEvent
import com.ivy.battery.ui.BatteryUiState
import com.ivy.battery.ui.component.BatterySection
import com.ivy.battery.ui.component.EmptyState
import com.ivy.battery.ui.component.InfoNote
import com.ivy.battery.ui.component.MetricRow
import com.ivy.battery.ui.component.SettingSliderRow
import com.ivy.battery.ui.component.SettingSwitchRow
import com.ivy.battery.ui.component.ShareBar
import com.ivy.battery.ui.component.StatTile
import com.ivy.battery.ui.component.StatTileRow
import com.ivy.battery.ui.format.formatCurrent
import com.ivy.battery.ui.format.formatDateTime
import com.ivy.battery.ui.format.formatDuration
import com.ivy.battery.ui.format.formatMah
import com.ivy.battery.ui.format.formatPercent
import com.ivy.battery.ui.format.formatPercentPerHour

@Composable
fun DischargeTab(
    state: BatteryUiState,
    onEvent: (BatteryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    val finishedSessions = state.dischargeSessions.filter { !it.ongoing }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            val ongoing = state.ongoingDischarge
            BatterySection(
                title = "Since you unplugged",
                subtitle = ongoing?.let { "Started ${formatDateTime(it.startedAt)}" }
                    ?: "Charging right now",
            ) {
                if (ongoing == null) {
                    EmptyState(
                        title = "On the charger",
                        message = "Discharge measurement resumes the moment you unplug.",
                    )
                } else {
                    StatTileRow(
                        left = {
                            StatTile(
                                label = "Screen-on drain",
                                value = formatPercentPerHour(ongoing.screenOnDrainPerHour),
                                icon = Icons.Rounded.LightMode,
                                accent = MaterialTheme.colorScheme.primary,
                                caption = "${formatCurrent(ongoing.avgScreenOnCurrentMa)} avg",
                            )
                        },
                        right = {
                            StatTile(
                                label = "Screen-off drain",
                                value = formatPercentPerHour(ongoing.screenOffDrainPerHour),
                                icon = Icons.Rounded.Bedtime,
                                caption = "${formatCurrent(ongoing.avgScreenOffCurrentMa)} avg",
                            )
                        },
                    )
                    StatTileRow(
                        left = {
                            StatTile(
                                label = "Used",
                                value = "${ongoing.levelDrained}%",
                                caption = formatMah(ongoing.screenOnMah + ongoing.screenOffMah),
                            )
                        },
                        right = {
                            StatTile(
                                label = "Unplugged for",
                                value = formatDuration(ongoing.durationMs),
                                icon = Icons.Rounded.Timer,
                            )
                        },
                    )

                    ScreenTimeSplit(ongoing)

                    StatTileRow(
                        left = {
                            StatTile(
                                label = "Deep sleep",
                                value = formatPercent(ongoing.deepSleepRatio * 100, 0),
                                caption = "of screen-off time",
                            )
                        },
                        right = {
                            StatTile(
                                label = "Awake",
                                value = formatDuration(ongoing.awakeMs),
                                caption = "CPU not sleeping",
                            )
                        },
                    )
                }
            }
        }

        item {
            BatterySection(
                title = "Low battery alarm",
            ) {
                SettingSwitchRow(
                    title = "Warn me when low",
                    description = "Deep discharges wear the cell out just like full charges do",
                    checked = settings.lowBatteryAlarmEnabled,
                    onCheckedChange = { onEvent(BatteryUiEvent.SetLowBatteryAlarmEnabled(it)) },
                )
                SettingSliderRow(
                    title = "Warn at",
                    valueLabel = "${settings.lowBatteryLevel}%",
                    value = settings.lowBatteryLevel.toFloat(),
                    range = LOW_MIN..LOW_MAX,
                    steps = LOW_STEPS,
                    onValueChange = { onEvent(BatteryUiEvent.SetLowBatteryLevel(it.toInt())) },
                )
            }
        }

        item {
            BatterySection(
                title = "Measured discharges",
                subtitle = "${finishedSessions.size} completed sessions",
            ) {
                if (finishedSessions.isEmpty()) {
                    EmptyState(
                        title = "Nothing measured yet",
                        message = "Use the phone off the charger for a while and the " +
                                "breakdown will show up here.",
                    )
                } else {
                    InfoNote(
                        text = "Screen-on and screen-off rates are kept apart on purpose: " +
                                "they usually differ by more than ten times, so a single " +
                                "blended number would tell you nothing useful."
                    )
                }
            }
        }

        items(finishedSessions, key = { it.id }) { session ->
            DischargeSessionCard(session)
        }
    }
}

@Composable
private fun ScreenTimeSplit(
    session: DischargeSession,
    modifier: Modifier = Modifier,
) {
    val total = (session.screenOnMs + session.screenOffMs).coerceAtLeast(1L)
    MetricRow(
        label = "Screen on",
        value = formatDuration(session.screenOnMs),
        modifier = modifier,
    )
    ShareBar(
        fraction = session.screenOnMs.toFloat() / total,
        color = MaterialTheme.colorScheme.primary,
    )
    MetricRow("Screen off", formatDuration(session.screenOffMs))
    ShareBar(
        fraction = session.screenOffMs.toFloat() / total,
        color = MaterialTheme.colorScheme.tertiary,
    )
}

@Composable
private fun DischargeSessionCard(
    session: DischargeSession,
    modifier: Modifier = Modifier,
) {
    BatterySection(
        title = formatDateTime(session.startedAt),
        subtitle = "${session.startLevel}% → ${session.endLevel}% · " +
                formatDuration(session.durationMs),
        modifier = modifier,
    ) {
        MetricRow("Screen-on drain", formatPercentPerHour(session.screenOnDrainPerHour))
        MetricRow("Screen-off drain", formatPercentPerHour(session.screenOffDrainPerHour))
        MetricRow("Screen-on time", formatDuration(session.screenOnMs))
        MetricRow("Screen-off time", formatDuration(session.screenOffMs))
        MetricRow("Energy used, screen on", formatMah(session.screenOnMah))
        MetricRow("Energy used, screen off", formatMah(session.screenOffMah))
        MetricRow("Deep sleep", formatDuration(session.deepSleepMs))
        MetricRow("Awake", formatDuration(session.awakeMs))
        MetricRow("Samples", session.sampleCount.toString())
    }
}

private const val LOW_MIN = 5f
private const val LOW_MAX = 50f
private const val LOW_STEPS = 8
