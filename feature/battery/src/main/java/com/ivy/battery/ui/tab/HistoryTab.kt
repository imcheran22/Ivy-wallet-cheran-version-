package com.ivy.battery.ui.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivy.battery.ui.BatteryUiEvent
import com.ivy.battery.ui.BatteryUiState
import com.ivy.battery.ui.HistoryRange
import com.ivy.battery.ui.component.BatterySection
import com.ivy.battery.ui.component.HistoryChart
import com.ivy.battery.ui.component.InfoNote
import com.ivy.battery.ui.component.MetricRow
import com.ivy.battery.ui.format.formatCurrent
import com.ivy.battery.ui.theme.BatteryLevelColors
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryTab(
    state: BatteryUiState,
    onEvent: (BatteryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClear by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                HistoryRange.entries.forEachIndexed { index, range ->
                    SegmentedButton(
                        selected = state.historyRange == range,
                        onClick = { onEvent(BatteryUiEvent.SelectHistoryRange(range)) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = HistoryRange.entries.size,
                        ),
                    ) {
                        Text(range.label)
                    }
                }
            }
        }

        item {
            BatterySection(
                title = "Battery level",
                subtitle = "Charging stretches are highlighted",
            ) {
                HistoryChart(
                    points = state.levelHistory,
                    lineColor = BatteryLevelColors.Good,
                    chargingColor = BatteryLevelColors.Charging,
                    minValue = 0f,
                    maxValue = 100f,
                    valueFormatter = { "${it.roundToInt()}%" },
                )
            }
        }

        item {
            BatterySection(title = "Temperature") {
                HistoryChart(
                    points = state.temperatureHistory,
                    lineColor = MaterialTheme.colorScheme.error,
                    chargingColor = BatteryLevelColors.Charging,
                    valueFormatter = { "${it.roundToInt()}°" },
                )
            }
        }

        item {
            BatterySection(
                title = "Current flow",
                subtitle = "Above zero is charging, below is drain",
            ) {
                HistoryChart(
                    points = state.currentHistory,
                    lineColor = MaterialTheme.colorScheme.tertiary,
                    chargingColor = BatteryLevelColors.Charging,
                    valueFormatter = { formatCurrent(it.toDouble()) },
                )
            }
        }

        item {
            BatterySection(title = "Stored data") {
                MetricRow("Samples in range", state.levelHistory.size.toString())
                MetricRow("Samples total", state.sampleCount.toString())
                MetricRow("Charge sessions", state.chargeSessions.size.toString())
                MetricRow("Discharge sessions", state.dischargeSessions.size.toString())
                MetricRow("Kept for", "${state.settings.retentionDays} days")

                Row(modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(
                        onClick = { onEvent(BatteryUiEvent.ExportCsv) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.IosShare, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Export CSV")
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(
                        onClick = { confirmClear = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.DeleteForever, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Clear")
                    }
                }
            }
        }

        item {
            InfoNote(
                text = "History is stored only on this device, in its own database, " +
                        "separate from your wallet data. Clearing it never touches your " +
                        "accounts or transactions."
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete battery history?") },
            text = {
                Text(
                    "Every sample, session and health measurement will be removed. " +
                            "Measuring starts again from scratch. Your wallet data is " +
                            "not affected."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEvent(BatteryUiEvent.ClearAllData)
                        confirmClear = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }
}
