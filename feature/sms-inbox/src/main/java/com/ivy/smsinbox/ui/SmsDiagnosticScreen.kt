package com.ivy.smsinbox.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel

@Composable
fun SmsDiagnosticScreenImpl(
    viewModel: SmsDiagnosticViewModel = screenScopedViewModel(),
) {
    val state by viewModel.state.collectAsState()
    SmsDiagnosticUi(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmsDiagnosticUi(
    state: SmsDiagnosticUiState,
    onEvent: (SmsDiagnosticEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nav = navigation()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { onEvent(SmsDiagnosticEvent.Scan) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = "SMS dry run", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
                    text = "Shows exactly what the parser would pull out of the messages " +
                        "already on this phone. Nothing is written until you import.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!state.permissionGranted) {
                item {
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.READ_SMS) }) {
                        Text("Allow reading SMS")
                    }
                }
                return@LazyColumn
            }

            item { ScanSummary(state = state, onEvent = onEvent) }

            if (state.senders.isNotEmpty()) {
                item {
                    Text(
                        text = "Who texts you about money",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(state.senders, key = { it.sender }) { SenderRow(summary = it) }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Message by message",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(state.rows) { DiagnosticRowCard(row = it) }
        }
    }
}

@Composable
private fun ScanSummary(
    state: SmsDiagnosticUiState,
    onEvent: (SmsDiagnosticEvent) -> Unit,
) {
    Column {
        Text(
            text = "${state.moneyLikeCount} of ${state.scannedCount} messages in the last " +
                "30 days look like money alerts.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onEvent(SmsDiagnosticEvent.ImportAll) },
                enabled = !state.importing && state.rows.any { it.parsed },
            ) {
                Text(if (state.importing) "Importing…" else "Import the parsed ones")
            }
        }
        state.importResult?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SenderRow(summary: SenderSummary) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            modifier = Modifier.weight(1f),
            text = summary.sender,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${summary.parsed}/${summary.moneyLike} parsed",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DiagnosticRowCard(row: DiagnosticRow) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = "${row.sender} · ${row.receivedLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (row.parsed) row.amountLabel.orEmpty() else "not parsed",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (row.isIncome) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            if (row.parsed) {
                Text(
                    text = "Payee: ${row.payee ?: "not named in the alert"}",
                    style = MaterialTheme.typography.bodySmall,
                )
                row.guessLabel?.let {
                    Text(text = "Guess: $it", style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            Text(
                text = row.preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
