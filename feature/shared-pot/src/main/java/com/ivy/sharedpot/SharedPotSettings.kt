package com.ivy.sharedpot

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which account the two of you treat as the shared pot, and what you agreed to keep it under.
 *
 * Deliberately only a pointer and a number. The pot itself is an ordinary account and the
 * spending is ordinary transactions, so everything else in the app - the ledger, reports,
 * cloud sync - already understands it. Point both phones at the same Supabase project and the
 * spending arrives on its own; this is only the agreement laid over it.
 *
 * The limit is stored per device rather than in the database on purpose: it is a number the two
 * of you decide out loud and re-decide often, and putting it in the schema would mean a
 * migration and a sync conflict for something a person can retype in three seconds.
 */
@Singleton
class SharedPotSettings @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    data class Config(
        val accountId: UUID?,
        val monthlyLimit: Double?,
    ) {
        val isSetUp: Boolean get() = accountId != null && (monthlyLimit ?: 0.0) > 0.0
    }

    suspend fun read(): Config {
        val prefs = dataStore.data.first()
        return Config(
            accountId = prefs[accountKey]?.let { runCatching { UUID.fromString(it) }.getOrNull() },
            monthlyLimit = prefs[limitKey],
        )
    }

    suspend fun setAccount(accountId: UUID) {
        dataStore.edit { it[accountKey] = accountId.toString() }
    }

    suspend fun setMonthlyLimit(limit: Double) {
        dataStore.edit { it[limitKey] = limit }
    }

    suspend fun clear() {
        dataStore.edit {
            it.remove(accountKey)
            it.remove(limitKey)
        }
    }

    private companion object {
        val accountKey = stringPreferencesKey("shared_pot_account_id")
        val limitKey = doublePreferencesKey("shared_pot_monthly_limit")
    }
}
