package com.notifmirror.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.google.gson.Gson
import com.notifmirror.util.MirrorPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class NotifListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == applicationContext.packageName) return

        val prefs = MirrorPrefs(applicationContext)
        if (prefs.mode != MirrorPrefs.MODE_SENDER) return

        val topicId = prefs.topicId ?: return
        val cloudFunctionUrl = prefs.cloudFunctionUrl ?: return

        val extras = sbn.notification.extras
        val appLabel = packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(sbn.packageName, 0)
        ).toString()

        val payload = mapOf(
            "topic" to topicId,
            "source_app" to appLabel,
            "source_package" to sbn.packageName,
            "title" to (extras.getCharSequence("android.title")?.toString() ?: ""),
            "text" to (extras.getCharSequence("android.text")?.toString() ?: ""),
            "timestamp" to sbn.postTime.toString()
        )

        scope.launch {
            try {
                val json = gson.toJson(payload)
                val body = json.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(cloudFunctionUrl)
                    .post(body)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        prefs.lastSyncTime = System.currentTimeMillis()
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // No action needed on removal
    }
}
