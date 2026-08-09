package com.ivy.notifmirror.sync

import android.provider.Settings
import com.ivy.base.CoupleTransactionSyncer
import com.ivy.notifmirror.data.MirrorPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoupleTransactionSyncerImpl @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val prefs: MirrorPrefs,
) : CoupleTransactionSyncer {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun syncTransaction(
        type: String,
        amount: Double,
        title: String,
        currency: String,
        category: String,
        accountName: String,
        dateTimeMillis: Long,
    ) {
        if (!prefs.setupComplete) return
        val topicId = prefs.topicId ?: return
        val relayUrl = prefs.cloudFunctionUrl ?: return

        val deviceId = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ANDROID_ID
        )

        val json = JSONObject().apply {
            put("topic", topicId)
            put("device_id", deviceId)
            put("type", type)
            put("amount", amount)
            put("title", title)
            put("currency", currency)
            put("category", category)
            put("account_name", accountName)
            put("date_time", dateTimeMillis.toString())
        }

        scope.launch {
            try {
                val body = json.toString()
                    .toRequestBody("application/json".toMediaType())
                val url = relayUrl.trimEnd('/') + "/sync-transaction"
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()
                client.newCall(request).execute().close()
            } catch (_: Exception) {
            }
        }
    }
}
