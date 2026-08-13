package com.ivy.notifmirror.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Every screen in this file paints its own background.
 *
 * `IvyUI` only wraps legacy screens in a `Surface`, so a modern screen that draws straight
 * onto the window inherits no background colour at all - which is how this screen ended up
 * rendering near-black text on a near-black window. A `Scaffold` with an explicit
 * `containerColor` is the fix, and it has to stay on each entry point.
 */
@Composable
fun NotifMirrorScreenImpl(
    viewModel: NotifMirrorViewModel = screenScopedViewModel(),
) {
    val state by viewModel.state.collectAsState()

    MirrorScaffold(
        title = when (state.screen) {
            MirrorScreen.MODE_SELECTION -> "Couple Mirror"
            MirrorScreen.TOPIC_SETUP -> "Pair the phones"
            MirrorScreen.NOTIF_ACCESS -> "Notification access"
            MirrorScreen.STATUS -> "Couple Mirror"
        },
    ) {
        when (state.screen) {
            MirrorScreen.MODE_SELECTION -> ModeSelectionStep(viewModel)
            MirrorScreen.TOPIC_SETUP -> TopicSetupStep(viewModel, state)
            MirrorScreen.NOTIF_ACCESS -> NotifAccessStep(viewModel)
            MirrorScreen.STATUS -> StatusStep(viewModel, state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MirrorScaffold(
    title: String,
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val nav = navigation()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text(text = title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { actions() },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            content()
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ModeSelectionStep(viewModel: NotifMirrorViewModel) {
    Text(
        text = "Mirror one phone's notifications onto the other, and keep both wallets in " +
            "step. Pick what this phone does.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(24.dp))

    RoleCard(
        title = "This phone sends",
        body = "Its notifications are forwarded to the other phone. Needs notification " +
            "access. Choose this on the phone you check less often.",
        buttonText = "Set up as sender",
        onClick = { viewModel.selectMode("sender") },
    )

    Spacer(Modifier.height(16.dp))

    RoleCard(
        title = "This phone receives",
        body = "Shows the other phone's notifications here, labelled with where they came " +
            "from. Choose this on the phone you actually carry.",
        buttonText = "Set up as receiver",
        onClick = { viewModel.selectMode("receiver") },
    )

    Spacer(Modifier.height(20.dp))

    Text(
        text = "Transactions sync both ways regardless of which role you pick.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RoleCard(
    title: String,
    body: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(buttonText) }
        }
    }
}

@Composable
private fun TopicSetupStep(viewModel: NotifMirrorViewModel, state: MirrorUiState) {
    val context = LocalContext.current
    val isSender = state.mode == "sender"

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* setup continues either way; the status screen re-asks if it was refused */ }

    Text(
        text = if (isSender) {
            "Generate a pairing code here, then type the same code on the other phone."
        } else {
            "Type the pairing code shown on the sender phone."
        },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(20.dp))

    OutlinedTextField(
        value = state.deviceLabel,
        onValueChange = viewModel::updateDeviceLabel,
        label = { Text("Name this phone") },
        supportingText = {
            Text("Shown on every notification this phone forwards, so the other phone knows who it was.")
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )

    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = state.topicId,
        onValueChange = viewModel::updateTopicId,
        label = { Text("Pairing code") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )

    if (isSender) {
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = { viewModel.updateTopicId("couple_mirror_" + UUID.randomUUID().toString().take(8)) },
        ) {
            Text("Generate a code")
        }
    }

    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = state.cloudFunctionUrl,
        onValueChange = viewModel::updateCloudFunctionUrl,
        label = { Text("Relay server URL") },
        placeholder = { Text("https://your-app.onrender.com") },
        supportingText = { Text("Both phones must point at the same relay.") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )

    Spacer(Modifier.height(24.dp))

    Button(
        onClick = {
            if (!viewModel.confirmTopicSetup()) {
                Toast.makeText(context, "Fill in the code and the relay URL", Toast.LENGTH_SHORT).show()
                return@Button
            }

            FirebaseMessaging.getInstance().subscribeToTopic(state.topicId.trim())
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Toast.makeText(context, "Could not reach the relay", Toast.LENGTH_LONG).show()
                        return@addOnCompleteListener
                    }
                    requestNotificationPermissionIfNeeded(context) {
                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    requestBatteryExemption(context)
                    if (isSender) viewModel.goToNotifAccess() else viewModel.completeSetup()
                }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Continue")
    }
}

@Composable
private fun NotifAccessStep(viewModel: NotifMirrorViewModel) {
    val context = LocalContext.current

    Text(
        text = "This phone needs permission to read its own notifications before it can " +
            "forward them.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(12.dp))

    Text(
        text = "Open the settings below, find this app in the list, and switch it on. " +
            "Android does not let an app grant this to itself.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(24.dp))

    Button(
        onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Open notification access settings")
    }

    Spacer(Modifier.height(12.dp))

    OutlinedButton(
        onClick = {
            if (viewModel.isNotificationListenerEnabled()) {
                viewModel.completeSetup()
            } else {
                Toast.makeText(context, "Not switched on yet", Toast.LENGTH_LONG).show()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("I've switched it on")
    }
}

@Composable
private fun StatusStep(viewModel: NotifMirrorViewModel, state: MirrorUiState) {
    val context = LocalContext.current
    val isSender = state.mode == "sender"
    val hasNotifPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshStatus() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = if (state.serviceActive) "Running" else "Not running",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (state.serviceActive) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Spacer(Modifier.height(10.dp))
            StatusRow("This phone", state.deviceLabel)
            StatusRow("Role", if (isSender) "Sending notifications" else "Receiving notifications")
            StatusRow("Pairing code", state.topicId.ifEmpty { "Not set" })
            StatusRow("Last sync", formatLastSync(state.lastSyncTime))
        }
    }

    if (isSender && !state.notifAccessGranted) {
        Spacer(Modifier.height(16.dp))
        WarningCard(
            title = "Notification access is off",
            body = "Nothing can be forwarded until this is switched on in Android settings.",
            actionText = "Open settings",
            onAction = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
        )
    }

    if (!hasNotifPermission) {
        Spacer(Modifier.height(16.dp))
        WarningCard(
            title = "This app cannot post notifications",
            body = "Mirrored alerts will arrive but stay invisible until you allow them.",
            actionText = "Allow notifications",
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
    }

    if (state.lastSyncTime == 0L) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Nothing has come through yet. Both phones need the same pairing code and " +
                "the same relay URL - and the sender needs notification access.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Spacer(Modifier.height(24.dp))

    OutlinedButton(
        onClick = { viewModel.refreshStatus() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Refresh")
    }

    Spacer(Modifier.height(8.dp))

    TextButton(onClick = { viewModel.resetSetup() }, modifier = Modifier.fillMaxWidth()) {
        Text("Unpair this phone", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun WarningCard(
    title: String,
    body: String,
    actionText: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(text = body, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAction) { Text(actionText) }
        }
    }
}

private fun formatLastSync(epochMs: Long): String = if (epochMs > 0) {
    SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(epochMs))
} else {
    "Never"
}

private inline fun requestNotificationPermissionIfNeeded(context: Context, launch: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
    if (!granted) launch()
}

private fun requestBatteryExemption(context: Context) {
    val pm = context.getSystemService(PowerManager::class.java)
    if (pm.isIgnoringBatteryOptimizations(context.packageName)) return
    context.startActivity(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
}
