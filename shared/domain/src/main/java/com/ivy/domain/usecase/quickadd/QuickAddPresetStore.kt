package com.ivy.domain.usecase.quickadd

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ivy.base.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the user's quick-add presets.
 *
 * Kept in DataStore rather than the Room ledger on purpose: presets are app configuration, not
 * financial history. Nothing here should ever end up in a balance, an export or a report.
 */
@Singleton
class QuickAddPresetStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val key = stringPreferencesKey("quick_add_presets")
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class StoredPreset(
        val id: String,
        val label: String,
        val amount: Double,
        val type: String,
        val categoryId: String? = null,
        val accountId: String? = null,
        val orderNum: Double = 0.0,
    )

    fun observe(): Flow<List<QuickAddPreset>> = dataStore.data.map { prefs ->
        decode(prefs[key])
    }

    suspend fun all(): List<QuickAddPreset> = observe().first()

    suspend fun findById(id: UUID): QuickAddPreset? = all().firstOrNull { it.id == id }

    /**
     * Adds a new preset or replaces the one with the same id, keeping the list capped so a
     * runaway list can never make the widget unrenderable.
     */
    suspend fun save(preset: QuickAddPreset) {
        val current = all().filterNot { it.id == preset.id }
        replaceAll((current + preset).take(QuickAddPreset.MAX_PRESETS))
    }

    suspend fun delete(id: UUID) {
        replaceAll(all().filterNot { it.id == id })
    }

    suspend fun replaceAll(presets: List<QuickAddPreset>) {
        val normalized = presets
            .sortedBy { it.orderNum }
            .mapIndexed { index, preset -> preset.copy(orderNum = index.toDouble()) }
        val encoded = json.encodeToString(normalized.map(::toStored))
        dataStore.edit { it[key] = encoded }
    }

    private fun decode(raw: String?): List<QuickAddPreset> {
        if (raw.isNullOrBlank()) return emptyList()
        val stored = runCatching {
            json.decodeFromString<List<StoredPreset>>(raw)
        }.getOrElse { return emptyList() }

        return stored.mapNotNull(::fromStored).sortedBy { it.orderNum }
    }

    private fun toStored(preset: QuickAddPreset) = StoredPreset(
        id = preset.id.toString(),
        label = preset.label,
        amount = preset.amount,
        type = preset.type.name,
        categoryId = preset.categoryId?.toString(),
        accountId = preset.accountId?.toString(),
        orderNum = preset.orderNum,
    )

    private fun fromStored(stored: StoredPreset): QuickAddPreset? = runCatching {
        QuickAddPreset(
            id = UUID.fromString(stored.id),
            label = stored.label,
            amount = stored.amount,
            type = TransactionType.valueOf(stored.type),
            categoryId = stored.categoryId?.let(UUID::fromString),
            accountId = stored.accountId?.let(UUID::fromString),
            orderNum = stored.orderNum,
        )
    }.getOrNull()
}
