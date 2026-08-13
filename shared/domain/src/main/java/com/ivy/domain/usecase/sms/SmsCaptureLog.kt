package com.ivy.domain.usecase.sms

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ivy.data.datastore.DatastoreKeys
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What auto-capture has actually done, in a form the user can read.
 *
 * Capture that runs in the background either works or fails silently, and silence looks
 * exactly like "no bank messages arrived". Every sweep writes here whether it found anything
 * or not, so Settings can answer "is this thing on?" with a timestamp and a count rather than
 * with a switch that claims to be enabled.
 */
data class SmsCaptureState(
    val lastSweepAt: Instant?,
    val lastCaptureAt: Instant?,
    val capturedTotal: Int,
    val lastSweepSummary: String?,
    /** Newest message this device has already been offered, so a sweep can start after it. */
    val watermark: Instant?,
)

@Singleton
class SmsCaptureLog @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    suspend fun read(): SmsCaptureState {
        val prefs = dataStore.data.first()
        return SmsCaptureState(
            lastSweepAt = prefs[lastSweepKey]?.let(Instant::ofEpochMilli),
            lastCaptureAt = prefs[lastCaptureKey]?.let(Instant::ofEpochMilli),
            capturedTotal = prefs[capturedTotalKey] ?: 0,
            lastSweepSummary = prefs[summaryKey],
            watermark = prefs[watermarkKey]?.let(Instant::ofEpochMilli),
        )
    }

    suspend fun isAutoCaptureEnabled(): Boolean =
        dataStore.data.first()[DatastoreKeys.SMS_AUTO_IMPORT_ENABLED] ?: false

    suspend fun recordSweep(
        imported: Int,
        summary: String,
        newestSeen: Instant?,
    ) {
        val now = Instant.now()
        dataStore.edit { prefs ->
            prefs[lastSweepKey] = now.toEpochMilli()
            prefs[summaryKey] = summary
            if (imported > 0) {
                prefs[lastCaptureKey] = now.toEpochMilli()
                prefs[capturedTotalKey] = (prefs[capturedTotalKey] ?: 0) + imported
            }
            // Only ever moves forward: a sweep that ran while the clock was skewed must not
            // rewind the watermark and re-offer months of messages on the next run.
            val previous = prefs[watermarkKey] ?: 0L
            val candidate = newestSeen?.toEpochMilli() ?: 0L
            if (candidate > previous) prefs[watermarkKey] = candidate
        }
    }

    private companion object {
        val lastSweepKey = longPreferencesKey("sms_capture_last_sweep_at")
        val lastCaptureKey = longPreferencesKey("sms_capture_last_capture_at")
        val capturedTotalKey = intPreferencesKey("sms_capture_total")
        val summaryKey = stringPreferencesKey("sms_capture_last_summary")
        val watermarkKey = longPreferencesKey("sms_capture_watermark")
    }
}
