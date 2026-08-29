package com.ivy.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.ivy.data.datastore.DatastoreKeys
import com.ivy.data.remote.supabase.SupabaseConfig
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject

data class CloudSyncPrefs(
    val enabled: Boolean,
    val supabaseUrl: String,
    val supabaseAnonKey: String,
    val lastSyncedEpochMs: Long?,
    /** What the last sync actually did, in words - success or the error it hit. */
    val lastResult: String? = null,
    val ownerId: String? = null,
)

class CloudSyncSettings @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun current(): CloudSyncPrefs {
        val prefs = dataStore.data.first()
        return CloudSyncPrefs(
            enabled = prefs[DatastoreKeys.CLOUD_SYNC_ENABLED] ?: false,
            supabaseUrl = prefs[DatastoreKeys.CLOUD_SYNC_SUPABASE_URL] ?: "",
            supabaseAnonKey = prefs[DatastoreKeys.CLOUD_SYNC_SUPABASE_ANON_KEY] ?: "",
            lastSyncedEpochMs = prefs[DatastoreKeys.CLOUD_SYNC_LAST_SYNCED_EPOCH_MS],
            lastResult = prefs[DatastoreKeys.CLOUD_SYNC_LAST_RESULT],
            ownerId = prefs[DatastoreKeys.CLOUD_SYNC_OWNER_ID],
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[DatastoreKeys.CLOUD_SYNC_ENABLED] = enabled }
    }

    suspend fun setCredentials(url: String, anonKey: String) {
        dataStore.edit {
            it[DatastoreKeys.CLOUD_SYNC_SUPABASE_URL] = url.trim()
            it[DatastoreKeys.CLOUD_SYNC_SUPABASE_ANON_KEY] = anonKey.trim()
        }
    }

    suspend fun setLastSyncedNow() {
        dataStore.edit {
            it[DatastoreKeys.CLOUD_SYNC_LAST_SYNCED_EPOCH_MS] = System.currentTimeMillis()
        }
    }

    suspend fun setLastResult(result: String) {
        dataStore.edit { it[DatastoreKeys.CLOUD_SYNC_LAST_RESULT] = result }
    }

    /**
     * Points this install at another one's data.
     *
     * The owner id is what scopes rows in Supabase, so two devices that share it share a
     * wallet. Generating one per install made this a backup; being able to set it is what
     * makes it sync.
     */
    suspend fun setOwnerId(ownerId: String) {
        val trimmed = ownerId.trim()
        if (trimmed.isBlank()) return
        dataStore.edit { it[DatastoreKeys.CLOUD_SYNC_OWNER_ID] = trimmed }
    }

    /** Stable per-install id used to scope rows in Supabase. Generated once, then persisted. */
    suspend fun ownerId(): String {
        val prefs = dataStore.data.first()
        prefs[DatastoreKeys.CLOUD_SYNC_OWNER_ID]?.let { return it }

        val newId = UUID.randomUUID().toString()
        dataStore.edit { it[DatastoreKeys.CLOUD_SYNC_OWNER_ID] = newId }
        return newId
    }

    /** Returns null if cloud sync isn't configured/enabled yet. */
    suspend fun supabaseConfigOrNull(): SupabaseConfig? {
        val prefs = current()
        if (!prefs.enabled || prefs.supabaseUrl.isBlank() || prefs.supabaseAnonKey.isBlank()) {
            return null
        }
        return SupabaseConfig(url = prefs.supabaseUrl, anonKey = prefs.supabaseAnonKey)
    }
}
