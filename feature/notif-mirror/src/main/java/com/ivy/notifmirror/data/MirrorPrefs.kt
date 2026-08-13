package com.ivy.notifmirror.data

import android.content.Context
import android.content.SharedPreferences

class MirrorPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    var mode: String?
        get() = prefs.getString(KEY_MODE, null)
        set(value) = prefs.edit().putString(KEY_MODE, value).apply()

    var topicId: String?
        get() = prefs.getString(KEY_TOPIC_ID, null)
        set(value) = prefs.edit().putString(KEY_TOPIC_ID, value).apply()

    var cloudFunctionUrl: String?
        get() = prefs.getString(KEY_CLOUD_FUNCTION_URL, null)
        set(value) = prefs.edit().putString(KEY_CLOUD_FUNCTION_URL, value).apply()

    var lastSyncTime: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    var setupComplete: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_COMPLETE, value).apply()

    /**
     * What this phone calls itself when it forwards a notification.
     *
     * Without it a mirrored alert is indistinguishable from one this phone raised itself -
     * same icon, same shade, no hint that it happened somewhere else. The label is the whole
     * point of mirroring: knowing *whose* phone the thing happened on.
     */
    var deviceLabel: String
        get() = prefs.getString(KEY_DEVICE_LABEL, null)?.takeIf { it.isNotBlank() }
            ?: android.os.Build.MODEL
        set(value) = prefs.edit().putString(KEY_DEVICE_LABEL, value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_FILE = "notif_mirror_prefs"
        private const val KEY_MODE = "notif_mirror_mode"
        private const val KEY_TOPIC_ID = "notif_mirror_topic_id"
        private const val KEY_CLOUD_FUNCTION_URL = "notif_mirror_cloud_function_url"
        private const val KEY_LAST_SYNC = "notif_mirror_last_sync"
        private const val KEY_SETUP_COMPLETE = "notif_mirror_setup_complete"
        private const val KEY_DEVICE_LABEL = "notif_mirror_device_label"

        const val MODE_SENDER = "sender"
        const val MODE_RECEIVER = "receiver"
    }
}
