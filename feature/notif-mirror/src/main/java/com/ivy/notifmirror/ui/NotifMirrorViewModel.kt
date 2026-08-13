package com.ivy.notifmirror.ui

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import com.ivy.notifmirror.data.MirrorPrefs
import com.ivy.notifmirror.service.MirrorForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class MirrorUiState(
    val screen: MirrorScreen = MirrorScreen.MODE_SELECTION,
    val mode: String? = null,
    val topicId: String = "",
    val cloudFunctionUrl: String = "",
    val lastSyncTime: Long = 0L,
    val serviceActive: Boolean = false,
    val deviceLabel: String = "",
    val notifAccessGranted: Boolean = false,
)

enum class MirrorScreen {
    MODE_SELECTION,
    TOPIC_SETUP,
    NOTIF_ACCESS,
    STATUS,
}

@HiltViewModel
class NotifMirrorViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val prefs: MirrorPrefs,
) : ViewModel() {

    private val _state = MutableStateFlow(MirrorUiState())
    val state: StateFlow<MirrorUiState> = _state.asStateFlow()

    init {
        _state.value = _state.value.copy(deviceLabel = prefs.deviceLabel)
        if (prefs.setupComplete) {
            refreshStatus()
            _state.value = _state.value.copy(screen = MirrorScreen.STATUS)
        }
    }

    fun selectMode(mode: String) {
        prefs.mode = mode
        _state.value = _state.value.copy(
            screen = MirrorScreen.TOPIC_SETUP,
            mode = mode,
        )
    }

    fun updateTopicId(id: String) {
        _state.value = _state.value.copy(topicId = id)
    }

    fun updateCloudFunctionUrl(url: String) {
        _state.value = _state.value.copy(cloudFunctionUrl = url)
    }

    fun updateDeviceLabel(label: String) {
        _state.value = _state.value.copy(deviceLabel = label)
    }

    fun confirmTopicSetup(): Boolean {
        val topicId = _state.value.topicId.trim()
        if (topicId.isEmpty()) return false

        val url = _state.value.cloudFunctionUrl.trim()
        if (url.isEmpty()) return false

        prefs.topicId = topicId
        prefs.cloudFunctionUrl = url
        _state.value.deviceLabel.trim().takeIf { it.isNotEmpty() }?.let { prefs.deviceLabel = it }

        return true
    }

    fun goToNotifAccess() {
        _state.value = _state.value.copy(screen = MirrorScreen.NOTIF_ACCESS)
    }

    fun isNotificationListenerEnabled(): Boolean {
        val cn = ComponentName(
            appContext,
            "com.ivy.notifmirror.service.NotifListenerService"
        )
        val flat = Settings.Secure.getString(
            appContext.contentResolver,
            "enabled_notification_listeners"
        )
        return flat != null && flat.contains(cn.flattenToString())
    }

    fun completeSetup() {
        prefs.setupComplete = true
        MirrorForegroundService.start(appContext)
        refreshStatus()
        _state.value = _state.value.copy(screen = MirrorScreen.STATUS)
    }

    fun refreshStatus() {
        _state.value = _state.value.copy(
            mode = prefs.mode,
            topicId = prefs.topicId ?: "",
            lastSyncTime = prefs.lastSyncTime,
            deviceLabel = prefs.deviceLabel,
            notifAccessGranted = isNotificationListenerEnabled(),
            serviceActive = if (prefs.mode == MirrorPrefs.MODE_SENDER) {
                isNotificationListenerEnabled()
            } else {
                true
            },
        )
    }

    fun resetSetup() {
        MirrorForegroundService.stop(appContext)
        val topicId = prefs.topicId
        if (topicId != null) {
            com.google.firebase.messaging.FirebaseMessaging.getInstance()
                .unsubscribeFromTopic(topicId)
        }
        prefs.clear()
        _state.value = MirrorUiState(deviceLabel = prefs.deviceLabel)
    }
}
