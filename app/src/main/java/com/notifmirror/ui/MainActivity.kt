package com.notifmirror.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.notifmirror.databinding.ActivityMainBinding
import com.notifmirror.service.MirrorForegroundService
import com.notifmirror.util.MirrorPrefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: MirrorPrefs

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* proceed regardless */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = MirrorPrefs(this)

        if (prefs.setupComplete) {
            showStatusScreen()
        } else {
            showModeSelection()
        }

        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        if (prefs.setupComplete) {
            updateStatus()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // ---- Screen 1: Mode Selection ----

    private fun showModeSelection() {
        binding.layoutModeSelection.visibility = View.VISIBLE
        binding.layoutTopicSetup.visibility = View.GONE
        binding.layoutNotifAccess.visibility = View.GONE
        binding.layoutStatus.visibility = View.GONE

        binding.btnSender.setOnClickListener {
            prefs.mode = MirrorPrefs.MODE_SENDER
            showTopicSetup()
        }
        binding.btnReceiver.setOnClickListener {
            prefs.mode = MirrorPrefs.MODE_RECEIVER
            showTopicSetup()
        }
    }

    // ---- Screen 2: Topic Setup ----

    private fun showTopicSetup() {
        binding.layoutModeSelection.visibility = View.GONE
        binding.layoutTopicSetup.visibility = View.VISIBLE
        binding.layoutNotifAccess.visibility = View.GONE
        binding.layoutStatus.visibility = View.GONE

        val isSender = prefs.mode == MirrorPrefs.MODE_SENDER

        binding.lblTopicInstructions.text = if (isSender) {
            "Generate a new Topic ID, then enter the same ID on your Receiver phone."
        } else {
            "Enter the Topic ID from your Sender phone."
        }

        binding.btnGenerateTopicId.visibility = if (isSender) View.VISIBLE else View.GONE
        binding.layoutCloudFunctionUrl.visibility = if (isSender) View.VISIBLE else View.GONE
        binding.lblCloudFunctionUrl.visibility = if (isSender) View.VISIBLE else View.GONE

        binding.btnGenerateTopicId.setOnClickListener {
            val id = "notif_mirror_" + UUID.randomUUID().toString().take(8)
            binding.inputTopicId.setText(id)
        }

        binding.btnTopicNext.setOnClickListener {
            val topicId = binding.inputTopicId.text.toString().trim()
            if (topicId.isEmpty()) {
                Toast.makeText(this, "Please enter a Topic ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isSender) {
                val url = binding.inputCloudFunctionUrl.text.toString().trim()
                if (url.isEmpty()) {
                    Toast.makeText(this, "Please enter the Cloud Function URL", Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener
                }
                prefs.cloudFunctionUrl = url
            }

            prefs.topicId = topicId

            if (isSender) {
                showNotifAccessScreen()
            } else {
                subscribeToTopicAndFinish(topicId)
            }
        }
    }

    // ---- Screen 3: Notification Access (Sender only) ----

    private fun showNotifAccessScreen() {
        binding.layoutModeSelection.visibility = View.GONE
        binding.layoutTopicSetup.visibility = View.GONE
        binding.layoutNotifAccess.visibility = View.VISIBLE
        binding.layoutStatus.visibility = View.GONE

        binding.btnOpenNotifAccess.setOnClickListener {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }

        binding.btnNotifAccessDone.setOnClickListener {
            if (isNotificationListenerEnabled()) {
                requestBatteryOptimizationExemption()
                finishSetup()
            } else {
                Toast.makeText(
                    this,
                    "Please enable Notification Access for this app first",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val cn = ComponentName(this, "com.notifmirror.service.NotifListenerService")
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    // ---- Setup Completion ----

    private fun subscribeToTopicAndFinish(topicId: String) {
        FirebaseMessaging.getInstance().subscribeToTopic(topicId)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    requestBatteryOptimizationExemption()
                    finishSetup()
                } else {
                    Toast.makeText(this, "Failed to subscribe to topic", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    private fun finishSetup() {
        prefs.setupComplete = true
        startForegroundService(Intent(this, MirrorForegroundService::class.java))
        showStatusScreen()
    }

    // ---- Screen 4: Status ----

    private fun showStatusScreen() {
        binding.layoutModeSelection.visibility = View.GONE
        binding.layoutTopicSetup.visibility = View.GONE
        binding.layoutNotifAccess.visibility = View.GONE
        binding.layoutStatus.visibility = View.VISIBLE

        startForegroundService(Intent(this, MirrorForegroundService::class.java))
        updateStatus()

        binding.btnReset.setOnClickListener {
            stopService(Intent(this, MirrorForegroundService::class.java))
            val topicId = prefs.topicId
            if (prefs.mode == MirrorPrefs.MODE_RECEIVER && topicId != null) {
                FirebaseMessaging.getInstance().unsubscribeFromTopic(topicId)
            }
            prefs.clear()
            showModeSelection()
        }

        binding.btnRefreshStatus.setOnClickListener {
            updateStatus()
        }
    }

    private fun updateStatus() {
        val mode = prefs.mode ?: "Not set"
        val topicId = prefs.topicId ?: "Not set"
        val lastSync = prefs.lastSyncTime
        val lastSyncText = if (lastSync > 0) {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(lastSync))
        } else {
            "Never"
        }

        val serviceActive = if (prefs.mode == MirrorPrefs.MODE_SENDER) {
            isNotificationListenerEnabled()
        } else {
            true
        }

        binding.txtMode.text = "Mode: ${mode.replaceFirstChar { it.uppercase() }}"
        binding.txtTopicId.text = "Topic: $topicId"
        binding.txtLastSync.text = "Last sync: $lastSyncText"
        binding.txtServiceStatus.text = "Service: ${if (serviceActive) "Active" else "Inactive"}"
        binding.txtServiceStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (serviceActive) android.R.color.holo_green_dark else android.R.color.holo_red_dark
            )
        )
    }
}
