package com.ivy.smsinbox.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.ivy.base.threading.DispatchersProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class DeviceSms(
    val sender: String,
    val body: String,
    val receivedAt: Instant,
)

/**
 * Reads messages already sitting in the device's SMS inbox.
 *
 * This exists for the diagnostic: before trusting any parsing rule, you want to see which
 * senders actually message you about money and what the rules would pull out of their real
 * text. A filter that is too narrow doesn't return "no data", it returns confident wrong data
 * - so the diagnostic reads everything in the window and lets the parser do the filtering.
 */
class DeviceSmsReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchersProvider: DispatchersProvider,
) {

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_SMS,
    ) == PackageManager.PERMISSION_GRANTED

    suspend fun readRecent(
        window: Duration = DEFAULT_WINDOW,
        limit: Int = DEFAULT_LIMIT,
    ): List<DeviceSms> = withContext(dispatchersProvider.io) {
        if (!hasPermission()) return@withContext emptyList()

        val since = Instant.now().minus(window).toEpochMilli()
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} DESC",
        ) ?: return@withContext emptyList()

        cursor.use {
            val addressIndex = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndexOrThrow(Telephony.Sms.DATE)

            buildList {
                while (it.moveToNext() && size < limit) {
                    val body = it.getString(bodyIndex) ?: continue
                    add(
                        DeviceSms(
                            sender = it.getString(addressIndex).orEmpty().ifBlank { "Unknown" },
                            body = body,
                            receivedAt = Instant.ofEpochMilli(it.getLong(dateIndex)),
                        )
                    )
                }
            }
        }
    }

    companion object {
        private val DEFAULT_WINDOW: Duration = Duration.ofDays(30)
        private const val DEFAULT_LIMIT = 500
    }
}
