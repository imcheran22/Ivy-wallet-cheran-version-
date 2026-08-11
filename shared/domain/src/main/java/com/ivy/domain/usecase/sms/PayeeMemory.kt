package com.ivy.domain.usecase.sms

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ivy.data.model.CategoryId
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers which category the user picked for a given payee, so they only ever have to
 * identify each one once.
 *
 * This is the core mechanic of the whole feature rather than a workaround: `K MANIKANTA` means
 * nothing to a computer, but once the user says it's the chai shop downstairs, every future
 * payment to that name files itself. The work shrinks every week instead of accumulating.
 */
@Singleton
@Suppress("ReturnCount")
class PayeeMemory @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val key = stringPreferencesKey("sms_payee_category_memory")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun all(): Map<String, CategoryId> = read().mapNotNull { (payee, rawId) ->
        runCatching { payee to CategoryId(UUID.fromString(rawId)) }.getOrNull()
    }.toMap()

    suspend fun categoryFor(payee: String?): CategoryId? {
        val normalized = normalize(payee) ?: return null
        val rawId = read()[normalized] ?: return null
        return runCatching { CategoryId(UUID.fromString(rawId)) }.getOrNull()
    }

    suspend fun remember(payee: String?, categoryId: CategoryId) {
        val normalized = normalize(payee) ?: return
        write(read() + (normalized to categoryId.value.toString()))
    }

    suspend fun forget(payee: String?) {
        val normalized = normalize(payee) ?: return
        write(read() - normalized)
    }

    /**
     * Drops every remembered payee that points at a category which no longer exists, so a
     * deleted category can't keep silently re-filing new transactions.
     */
    suspend fun forgetCategoriesOtherThan(existing: Set<CategoryId>) {
        val existingIds = existing.map { it.value.toString() }.toSet()
        write(read().filterValues { it in existingIds })
    }

    private suspend fun read(): Map<String, String> {
        val raw = dataStore.data.first()[key] ?: return emptyMap()
        return runCatching {
            json.decodeFromString<Map<String, String>>(raw)
        }.getOrDefault(emptyMap())
    }

    private suspend fun write(value: Map<String, String>) {
        dataStore.edit { it[key] = json.encodeToString(value) }
    }

    companion object {
        /**
         * Payees have to match across alerts that differ only in casing and spacing, but not
         * so loosely that two different people collapse into one.
         */
        fun normalize(payee: String?): String? = payee
            ?.trim()
            ?.lowercase()
            ?.replace(Regex("\\s+"), " ")
            ?.takeIf { it.isNotBlank() }
    }
}
