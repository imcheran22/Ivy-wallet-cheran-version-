package com.ivy.settings.cloudsync

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.data.sync.RestorePreview
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CloudSyncScreenImpl(
    viewModel: CloudSyncViewModel = screenScopedViewModel(),
) {
    val state by viewModel.state.collectAsState()
    CloudSyncUi(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloudSyncUi(
    state: CloudSyncState,
    onEvent: (CloudSyncEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nav = navigation()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.cloud_sync), fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!state.configured) {
                Text(
                    text = stringResource(R.string.cloud_sync_not_configured),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            StatusCard(state = state)

            PairingCard(
                state = state,
                onCopy = { copyToClipboard(context, it) },
                onEvent = onEvent,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = state.configured && !state.busy,
                    onClick = { onEvent(CloudSyncEvent.SyncNow) },
                ) {
                    Text(stringResource(R.string.sync_now))
                }
                OutlinedButton(
                    enabled = state.configured && !state.busy,
                    onClick = { onEvent(CloudSyncEvent.PreviewRestore) },
                ) {
                    Text(stringResource(R.string.restore_from_cloud))
                }
            }

            state.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    state.preview?.let { preview ->
        RestorePreviewDialog(
            preview = preview,
            onConfirm = { onEvent(CloudSyncEvent.ConfirmRestore) },
            onDismiss = { onEvent(CloudSyncEvent.DismissPreview) },
        )
    }
}

@Composable
private fun StatusCard(state: CloudSyncState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.sync_status),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (state.enabled) {
                    stringResource(R.string.sync_on)
                } else {
                    stringResource(R.string.sync_off)
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = state.lastSyncedEpochMs?.let {
                    stringResource(R.string.last_synced, formatTime(it))
                } ?: stringResource(R.string.never_synced),
                style = MaterialTheme.typography.bodyMedium,
            )
            state.lastResult?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Pairing is what makes two installs one wallet: they have to agree on the owner id that scopes
 * every row, and until now each install invented its own and could never be told otherwise.
 */
@Composable
private fun PairingCard(
    state: CloudSyncState,
    onCopy: (String) -> Unit,
    onEvent: (CloudSyncEvent) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.pair_devices),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.pair_devices_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.this_device_code),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = state.ownerId,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )

            TextButton(onClick = { onCopy(state.ownerId) }) {
                Text(stringResource(R.string.copy_code))
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.pairingInput,
                onValueChange = { onEvent(CloudSyncEvent.SetPairingInput(it)) },
                label = { Text(stringResource(R.string.paste_other_device_code)) },
                singleLine = true,
            )

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    enabled = state.pairingInput.isNotBlank(),
                    onClick = { onEvent(CloudSyncEvent.PairWithCode) },
                ) {
                    Text(stringResource(R.string.pair))
                }

                if (state.paired) {
                    Text(
                        text = stringResource(R.string.paired_confirmation),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun RestorePreviewDialog(
    preview: RestorePreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_from_cloud)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (preview.remoteIsEmpty) {
                        stringResource(R.string.restore_preview_empty)
                    } else {
                        stringResource(R.string.restore_preview_warning)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                PreviewRow(
                    label = stringResource(R.string.accounts),
                    remote = preview.remoteAccounts,
                    local = preview.localAccounts,
                )
                PreviewRow(
                    label = stringResource(R.string.categories),
                    remote = preview.remoteCategories,
                    local = preview.localCategories,
                )
                PreviewRow(
                    label = stringResource(R.string.transactions_title),
                    remote = preview.remoteTransactions,
                    local = preview.localTransactions,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !preview.remoteIsEmpty,
                onClick = onConfirm,
            ) {
                Text(stringResource(R.string.restore))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun PreviewRow(label: String, remote: Int, local: Int) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.restore_preview_counts, remote, local),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun copyToClipboard(context: Context, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Ivy sync code", value))
}

private fun formatTime(epochMs: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(epochMs))
