package com.ivy.notifmirror.ui

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ivy.navigation.screenScopedViewModel
import com.google.firebase.messaging.FirebaseMessaging
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun NotifMirrorScreenImpl(
    viewModel: NotifMirrorViewModel = screenScopedViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        when (state.screen) {
            MirrorScreen.MODE_SELECTION -> ModeSelectionScreen(viewModel)
            MirrorScreen.TOPIC_SETUP -> TopicSetupScreen(viewModel, state)
            MirrorScreen.NOTIF_ACCESS -> NotifAccessScreen(viewModel)
            MirrorScreen.STATUS -> StatusScreen(viewModel, state)
        }
    }
}

@Composable
private fun ModeSelectionScreen(viewModel: NotifMirrorViewModel) {
    Text(
        text = "Notification Mirror",
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Choose how this phone will be used:",
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(32.dp))

    androidx.compose.material3.Button(
        onClick = { viewModel.selectMode("sender") },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Sender (Secondary Phone)")
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = "Captures notifications and sends them to your primary phone.",
        style = MaterialTheme.typography.bodySmall,
    )

    Spacer(Modifier.height(24.dp))

    androidx.compose.material3.Button(
        onClick = { viewModel.selectMode("receiver") },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Receiver (Primary Phone)")
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = "Receives and displays notifications from your secondary phone.",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun TopicSetupScreen(viewModel: NotifMirrorViewModel, state: MirrorUiState) {
    val context = LocalContext.current
    val isSender = state.mode == "sender"

    Text(
        text = "Topic Setup",
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = if (isSender) {
            "Generate a new Topic ID, then enter the same ID on your Receiver phone."
        } else {
            "Enter the Topic ID from your Sender phone."
        },
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(24.dp))

    if (isSender) {
        TextButton(
            onClick = {
                val id = "notif_mirror_" + UUID.randomUUID().toString().take(8)
                viewModel.updateTopicId(id)
            },
        ) {
            Text("Generate Topic ID")
        }
        Spacer(Modifier.height(8.dp))
    }

    OutlinedTextField(
        value = state.topicId,
        onValueChange = { viewModel.updateTopicId(it) },
        label = { Text("Topic ID") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )

    if (isSender) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Enter your Cloud Function URL (from Firebase deploy output):",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.cloudFunctionUrl,
            onValueChange = { viewModel.updateCloudFunctionUrl(it) },
            label = { Text("Cloud Function URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }

    Spacer(Modifier.height(24.dp))

    androidx.compose.material3.Button(
        onClick = {
            val ok = viewModel.confirmTopicSetup()
            if (!ok) {
                Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@Button
            }
            if (!isSender) {
                val topicId = state.topicId.trim()
                FirebaseMessaging.getInstance().subscribeToTopic(topicId)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            requestBatteryExemption(context)
                            viewModel.completeSetup()
                        } else {
                            Toast.makeText(
                                context, "Failed to subscribe", Toast.LENGTH_LONG
                            ).show()
                        }
                    }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Next")
    }
}

@Composable
private fun NotifAccessScreen(viewModel: NotifMirrorViewModel) {
    val context = LocalContext.current

    Text(
        text = "Notification Access",
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "This app needs permission to read notifications so it can mirror them " +
            "to your other phone.\n\nTap the button below to open Settings, " +
            "then enable this app.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(24.dp))

    androidx.compose.material3.Button(
        onClick = {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Open Notification Access Settings")
    }

    Spacer(Modifier.height(16.dp))

    OutlinedButton(
        onClick = {
            if (viewModel.isNotificationListenerEnabled()) {
                requestBatteryExemption(context)
                viewModel.completeSetup()
            } else {
                Toast.makeText(
                    context,
                    "Please enable Notification Access first",
                    Toast.LENGTH_LONG,
                ).show()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("I've Enabled It — Continue")
    }
}

@Composable
private fun StatusScreen(viewModel: NotifMirrorViewModel, state: MirrorUiState) {
    val lastSyncText = if (state.lastSyncTime > 0) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(state.lastSyncTime))
    } else {
        "Never"
    }

    Text(
        text = "Notification Mirror",
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(Modifier.height(24.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Service: ${if (state.serviceActive) "Active" else "Inactive"}",
                style = MaterialTheme.typography.titleMedium,
                color = if (state.serviceActive) {
                    Color(0xFF2E7D32)
                } else {
                    Color(0xFFC62828)
                },
            )
            Text(
                text = "Mode: ${state.mode?.replaceFirstChar { it.uppercase() } ?: "Not set"}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "Topic: ${state.topicId.ifEmpty { "Not set" }}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "Last sync: $lastSyncText",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }

    Spacer(Modifier.height(24.dp))

    OutlinedButton(
        onClick = { viewModel.refreshStatus() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Refresh Status")
    }

    Spacer(Modifier.height(12.dp))

    TextButton(
        onClick = { viewModel.resetSetup() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Reset Setup")
    }
}

private fun requestBatteryExemption(context: android.content.Context) {
    val pm = context.getSystemService(PowerManager::class.java)
    if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
