package com.ivy.domain.usecase.budget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which budgets roll over.
 *
 * Rollover is a property of how the user budgets, not of the amount, so it lives beside the app's
 * other preferences rather than in the ledger - and a budget that loses its rollover flag still
 * behaves like a perfectly ordinary budget.
 */
@Singleton
class BudgetPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val key = stringSetPreferencesKey("budget_rollover_ids")

    suspend fun rolloverBudgetIds(): Set<UUID> = dataStore.data
        .map { prefs -> prefs[key].orEmpty() }
        .first()
        .mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
        .toSet()

    suspend fun setRollover(budgetId: UUID, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[key].orEmpty().toMutableSet()
            if (enabled) {
                current.add(budgetId.toString())
            } else {
                current.remove(budgetId.toString())
            }
            prefs[key] = current
        }
    }
}
