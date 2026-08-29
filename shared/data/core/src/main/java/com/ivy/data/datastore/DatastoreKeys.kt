package com.ivy.data.datastore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object DatastoreKeys {
    @Deprecated("will be removed")
    val GITHUB_OWNER = stringPreferencesKey("github_backup_owner")

    @Deprecated("will be removed")
    val GITHUB_REPO = stringPreferencesKey("github_backup_repo")

    @Deprecated("will be removed")
    val GITHUB_PAT = stringPreferencesKey("github_backup_pat")

    @Deprecated("will be removed")
    val GITHUB_LAST_BACKUP_EPOCH_SEC =
        longPreferencesKey("github_backup_last_backup_time_epoch_sec")

    // SMS auto-import
    val SMS_AUTO_IMPORT_ENABLED = booleanPreferencesKey("sms_auto_import_enabled")

    /**
     * Nothing older than this is ever imported. Set when auto-import is first switched on, and
     * editable in Settings, so turning capture on does not backfill months of old alerts.
     */
    val SMS_IMPORT_FROM_EPOCH_MS = longPreferencesKey("sms_import_from_epoch_ms")

    // Cloud sync (Supabase)
    val CLOUD_SYNC_ENABLED = booleanPreferencesKey("cloud_sync_enabled")
    val CLOUD_SYNC_SUPABASE_URL = stringPreferencesKey("cloud_sync_supabase_url")
    val CLOUD_SYNC_SUPABASE_ANON_KEY = stringPreferencesKey("cloud_sync_supabase_anon_key")

    // Random, locally-generated id used to scope this install's rows in Supabase
    // (via a `owner_id` column + RLS policy). Not a real authentication mechanism.
    val CLOUD_SYNC_OWNER_ID = stringPreferencesKey("cloud_sync_owner_id")
    val CLOUD_SYNC_LAST_SYNCED_EPOCH_MS = longPreferencesKey("cloud_sync_last_synced_epoch_ms")

    fun ivyFeature(key: String): Preferences.Key<Boolean> {
        return booleanPreferencesKey("feature_$key")
    }
}
