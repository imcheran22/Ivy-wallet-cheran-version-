package com.ivy.battery.ui.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivy.battery.domain.model.AppDrain
import com.ivy.battery.ui.BatteryUiEvent
import com.ivy.battery.ui.BatteryUiState
import com.ivy.battery.ui.component.BatterySection
import com.ivy.battery.ui.component.EmptyState
import com.ivy.battery.ui.component.InfoNote
import com.ivy.battery.ui.component.SettingSwitchRow
import com.ivy.battery.ui.component.ShareBar
import com.ivy.battery.ui.format.formatDuration
import com.ivy.battery.ui.format.formatMah
import com.ivy.battery.ui.format.formatPercent

@Composable
fun AppsTab(
    state: BatteryUiState,
    onEvent: (BatteryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val topDrain = state.appDrain.maxOfOrNull { it.estimatedMah } ?: 0.0

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (!state.hasUsageAccess) {
            item {
                BatterySection(
                    title = "Usage access needed",
                    subtitle = "One-time permission, granted in Android settings",
                ) {
                    Text(
                        text = "Android never tells third-party apps how much battery each " +
                                "app used - that data is reserved for the system. What can " +
                                "be measured is how long each app actually held the screen, " +
                                "which is what this breakdown is built from. It needs the " +
                                "\"Usage access\" special permission.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = { onEvent(BatteryUiEvent.RequestUsageAccess) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Open usage access settings")
                    }
                }
            }
        }

        item {
            BatterySection(
                title = "Battery use by app",
                subtitle = "Last ${state.historyRange.label.lowercase()}",
                trailing = {
                    FilledTonalButton(onClick = { onEvent(BatteryUiEvent.RefreshAppDrain) }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Refresh")
                    }
                },
            ) {
                SettingSwitchRow(
                    title = "Track per-app drain",
                    description = "Attributes each discharge session's screen-on energy " +
                            "to the apps that were in the foreground",
                    checked = state.settings.trackAppDrain,
                    onCheckedChange = { onEvent(BatteryUiEvent.SetTrackAppDrain(it)) },
                )

                if (state.appDrain.isEmpty()) {
                    EmptyState(
                        title = "Nothing attributed yet",
                        message = if (state.hasUsageAccess) {
                            "Use the phone off the charger for a while, then refresh."
                        } else {
                            "Grant usage access above to start attributing drain to apps."
                        },
                    )
                }
            }
        }

        items(state.appDrain, key = { it.packageName }) { drain ->
            AppDrainRow(drain = drain, maxMah = topDrain)
        }

        item {
            InfoNote(
                text = "These are attributions, not hardware measurements: the measured " +
                        "screen-on energy of each session is split in proportion to " +
                        "foreground time. Background work an app does with the screen off " +
                        "is not visible to any app on the Play Store, including this one."
            )
        }
    }
}

@Composable
private fun AppDrainRow(
    drain: AppDrain,
    maxMah: Double,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = drain.appLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatDuration(drain.foregroundMs)} in the foreground",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatMah(drain.estimatedMah),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = formatPercent(drain.estimatedPercent, 1),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        ShareBar(
            fraction = if (maxMah > 0) (drain.estimatedMah / maxMah).toFloat() else 0f,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
