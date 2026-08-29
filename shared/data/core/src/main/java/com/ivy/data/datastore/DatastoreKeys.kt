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

    // Cloud sync (Supabase)
    val CLOUD_SYNC_ENABLED = booleanPreferencesKey("cloud_sync_enabled")
    val CLOUD_SYNC_SUPABASE_URL = stringPreferencesKey("cloud_sync_supabase_url")
    val CLOUD_SYNC_SUPABASE_ANON_KEY = stringPreferencesKey("cloud_sync_supabase_anon_key")

    // Random, locally-generated id used to scope this install's rows in Supabase
    // (via a `owner_id` column + RLS policy). Not a real authentication mechanism.
    val CLOUD_SYNC_OWNER_ID = stringPreferencesKey("cloud_sync_owner_id")
    val CLOUD_SYNC_LAST_SYNCED_EPOCH_MS = longPreferencesKey("cloud_sync_last_synced_epoch_ms")

    // Quick add
    val QUICK_ADD_NOTIFICATION_ENABLED = booleanPreferencesKey("quick_add_notification_enabled")
    val DAILY_SUMMARY_ENABLED = booleanPreferencesKey("daily_summary_enabled")

    // Privacy
    val SECURE_SCREEN_ENABLED = booleanPreferencesKey("secure_screen_enabled")
    val HIDE_AMOUNTS = booleanPreferencesKey("hide_amounts")

    // Backups
    val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
    val AUTO_BACKUP_LAST_RUN_EPOCH_MS = longPreferencesKey("auto_backup_last_run_epoch_ms")
    val AUTO_BACKUP_LAST_RESULT = stringPreferencesKey("auto_backup_last_result")

    fun ivyFeature(key: String): Preferences.Key<Boolean> {
        return booleanPreferencesKey("feature_$key")
    }
}
