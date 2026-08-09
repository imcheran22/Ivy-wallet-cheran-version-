package com.ivy.notifmirror.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.ivy.notifmirror.data.MirrorPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class NotifListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == applicationContext.packageName) return

        val prefs = MirrorPrefs(applicationContext)
        if (prefs.mode != MirrorPrefs.MODE_SENDER) return

        val topicId = prefs.topicId ?: return
        val cloudFunctionUrl = prefs.cloudFunctionUrl ?: return

        val extras = sbn.notification.extras
        val appLabel = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        } catch (_: Exception) {
            sbn.packageName
        }

        val json = JSONObject().apply {
            put("topic", topicId)
            put("source_app", appLabel)
            put("source_package", sbn.packageName)
            put("title", extras.getCharSequence("android.title")?.toString() ?: "")
            put("text", extras.getCharSequence("android.text")?.toString() ?: "")
            put("timestamp", sbn.postTime.toString())
        }

        scope.launch {
            try {
                val body = json.toString()
                    .toRequestBody("application/json".toMediaType())
                val url = cloudFunctionUrl.trimEnd('/') + "/mirror"
                val request = Request.Builder()
                    .url(url)
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
    }
}
