package com.ivy.settings.quickadd

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.ivy.base.model.TransactionType
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import java.text.DecimalFormat

private val amountFormat = DecimalFormat("###,###.##")

@Composable
fun QuickAddSettingsScreenImpl(
    viewModel: QuickAddSettingsViewModel = screenScopedViewModel(),
) {
    val state by viewModel.state.collectAsState()
    QuickAddSettingsUi(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddSettingsUi(
    state: QuickAddSettingsState,
    onEvent: (QuickAddSettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nav = navigation()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.quick_add),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.quick_add_presets_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.presets.isEmpty()) {
                item {
                    Text(
                        modifier = Modifier.padding(vertical = 16.dp),
                        text = stringResource(R.string.no_presets_yet),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            items(state.presets.size) { index ->
                val preset = state.presets[index]
                PresetCard(
                    preset = preset,
                    canMoveUp = index > 0,
                    canMoveDown = index < state.presets.size - 1,
                    onEvent = onEvent,
                )
            }

            item {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.presetLimitReached,
                    onClick = { onEvent(QuickAddSettingsEvent.AddPreset) },
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(stringResource(R.string.add_preset))
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                SettingSwitch(
                    title = stringResource(R.string.quick_add_notification),
                    description = stringResource(R.string.quick_add_notification_description),
                    checked = state.notificationEnabled,
                    onCheckedChange = {
                        onEvent(QuickAddSettingsEvent.SetNotificationEnabled(it))
                    },
                )
            }

            item {
                SettingSwitch(
                    title = stringResource(R.string.daily_summary_notification),
                    description = stringResource(R.string.daily_summary_notification_description),
                    checked = state.dailySummaryEnabled,
                    onCheckedChange = {
                        onEvent(QuickAddSettingsEvent.SetDailySummaryEnabled(it))
                    },
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    state.draft?.let { draft ->
        PresetDialog(state = state, draft = draft, onEvent = onEvent)
    }
}

@Composable
private fun PresetCard(
    preset: PresetRow,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEvent: (QuickAddSettingsEvent) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${preset.label} · ${amountFormat.format(preset.amount)} ${preset.currency}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        typeLabel(preset.type),
                        preset.categoryName,
                        preset.accountName,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(
                enabled = canMoveUp,
                onClick = { onEvent(QuickAddSettingsEvent.Move(preset.id, up = true)) },
            ) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null)
            }
            IconButton(
                enabled = canMoveDown,
                onClick = { onEvent(QuickAddSettingsEvent.Move(preset.id, up = false)) },
            ) {
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
            }
            IconButton(onClick = { onEvent(QuickAddSettingsEvent.EditPreset(preset.id)) }) {
                Icon(Icons.Rounded.Edit, contentDescription = null)
            }
            IconButton(onClick = { onEvent(QuickAddSettingsEvent.DeletePreset(preset.id)) }) {
                Icon(Icons.Rounded.Delete, contentDescription = null)
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PresetDialog(
    state: QuickAddSettingsState,
    draft: PresetDraft,
    onEvent: (QuickAddSettingsEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(QuickAddSettingsEvent.DismissDraft) },
        title = {
            Text(
                stringResource(
                    if (draft.id == null) R.string.add_preset else R.string.edit_preset
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.label,
                    onValueChange = {
                        onEvent(QuickAddSettingsEvent.UpdateDraft(draft.copy(label = it)))
                    },
                    label = { Text(stringResource(R.string.preset_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = draft.amount,
                    onValueChange = {
                        onEvent(QuickAddSettingsEvent.UpdateDraft(draft.copy(amount = it)))
                    },
                    label = { Text(stringResource(R.string.amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(TransactionType.EXPENSE, TransactionType.INCOME).forEach { type ->
                        FilterChip(
                            selected = draft.type == type,
                            onClick = {
                                onEvent(QuickAddSettingsEvent.UpdateDraft(draft.copy(type = type)))
                            },
                            label = { Text(typeLabel(type)) },
                        )
                    }
                }

                ChipPicker(
                    label = stringResource(R.string.category),
                    options = state.categories.map { it.id to it.name },
                    selectedId = draft.categoryId,
                    onSelect = {
                        onEvent(QuickAddSettingsEvent.UpdateDraft(draft.copy(categoryId = it)))
                    },
                )

                ChipPicker(
                    label = stringResource(R.string.account),
                    options = state.accounts.map { it.id to it.name },
                    selectedId = draft.accountId,
                    onSelect = {
                        onEvent(QuickAddSettingsEvent.UpdateDraft(draft.copy(accountId = it)))
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = draft.isValid,
                onClick = { onEvent(QuickAddSettingsEvent.SaveDraft) },
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(QuickAddSettingsEvent.DismissDraft) }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/**
 * A single row of chips instead of a dropdown: on a phone, tapping the thing you want beats
 * opening a menu to tap the thing you want.
 */
@Composable
private fun ChipPicker(
    label: String,
    options: List<Pair<java.util.UUID, String>>,
    selectedId: java.util.UUID?,
    onSelect: (java.util.UUID?) -> Unit,
) {
    if (options.isEmpty()) return

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (id, name) ->
                FilterChip(
                    selected = selectedId == id,
                    onClick = { onSelect(if (selectedId == id) null else id) },
                    label = { Text(name, maxLines = 1) },
                )
            }
        }
    }
}

@Composable
private fun typeLabel(type: TransactionType): String = stringResource(
    when (type) {
        TransactionType.INCOME -> R.string.income
        TransactionType.EXPENSE -> R.string.expense
        TransactionType.TRANSFER -> R.string.transfer
    }
)
