package com.ivy.domain.usecase.filters

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ivy.base.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A named report filter.
 *
 * Stores ids rather than whole accounts and categories, so a saved filter survives an account
 * being renamed and quietly drops one that was deleted instead of failing to load.
 *
 * The period is deliberately not saved: "food, cash, this month" should mean *this* month every
 * time it's used, not the month it happened to be saved in.
 */
data class SavedFilter(
    val id: UUID,
    val name: String,
    val trnTypes: List<TransactionType>,
    val accountIds: List<UUID>,
    val categoryIds: List<UUID>,
    val minAmount: Double?,
    val maxAmount: Double?,
    val includeKeywords: List<String>,
    val excludeKeywords: List<String>,
)

@Singleton
class SavedFilterStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val key = stringPreferencesKey("report_saved_filters")
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class StoredFilter(
        val id: String,
        val name: String,
        val trnTypes: List<String> = emptyList(),
        val accountIds: List<String> = emptyList(),
        val categoryIds: List<String> = emptyList(),
        val minAmount: Double? = null,
        val maxAmount: Double? = null,
        val includeKeywords: List<String> = emptyList(),
        val excludeKeywords: List<String> = emptyList(),
    )

    suspend fun all(): List<SavedFilter> = decode(dataStore.data.first()[key])

    suspend fun findById(id: UUID): SavedFilter? = all().firstOrNull { it.id == id }

    suspend fun save(filter: SavedFilter) {
        replaceAll(all().filterNot { it.id == filter.id } + filter)
    }

    suspend fun delete(id: UUID) {
        replaceAll(all().filterNot { it.id == id })
    }

    private suspend fun replaceAll(filters: List<SavedFilter>) {
        val encoded = json.encodeToString(filters.map(::toStored))
        dataStore.edit { it[key] = encoded }
    }

    private fun decode(raw: String?): List<SavedFilter> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<StoredFilter>>(raw) }
            .getOrDefault(emptyList())
            .mapNotNull(::fromStored)
    }

    private fun toStored(filter: SavedFilter) = StoredFilter(
        id = filter.id.toString(),
        name = filter.name,
        trnTypes = filter.trnTypes.map { it.name },
        accountIds = filter.accountIds.map(UUID::toString),
        categoryIds = filter.categoryIds.map(UUID::toString),
        minAmount = filter.minAmount,
        maxAmount = filter.maxAmount,
        includeKeywords = filter.includeKeywords,
        excludeKeywords = filter.excludeKeywords,
    )

    private fun fromStored(stored: StoredFilter): SavedFilter? = runCatching {
        SavedFilter(
            id = UUID.fromString(stored.id),
            name = stored.name,
            trnTypes = stored.trnTypes.mapNotNull {
                runCatching { TransactionType.valueOf(it) }.getOrNull()
            },
            accountIds = stored.accountIds.mapNotNull {
                runCatching { UUID.fromString(it) }.getOrNull()
            },
            categoryIds = stored.categoryIds.mapNotNull {
                runCatching { UUID.fromString(it) }.getOrNull()
            },
            minAmount = stored.minAmount,
            maxAmount = stored.maxAmount,
            includeKeywords = stored.includeKeywords,
            excludeKeywords = stored.excludeKeywords,
        )
    }.getOrNull()
}
