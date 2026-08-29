package com.ivy.domain.usecase.trip

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val key = stringPreferencesKey("trips")
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class StoredTrip(
        val id: String,
        val name: String,
        val startDate: String,
        val endDate: String,
        val accountIds: List<String> = emptyList(),
        val currency: String? = null,
    )

    suspend fun all(): List<Trip> = decode(dataStore.data.first()[key])
        .sortedByDescending { it.startDate }

    suspend fun findById(id: UUID): Trip? = all().firstOrNull { it.id == id }

    suspend fun save(trip: Trip) {
        replaceAll(all().filterNot { it.id == trip.id } + trip)
    }

    suspend fun delete(id: UUID) {
        replaceAll(all().filterNot { it.id == id })
    }

    private suspend fun replaceAll(trips: List<Trip>) {
        val encoded = json.encodeToString(trips.map(::toStored))
        dataStore.edit { it[key] = encoded }
    }

    private fun decode(raw: String?): List<Trip> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<StoredTrip>>(raw) }
            .getOrDefault(emptyList())
            .mapNotNull(::fromStored)
    }

    private fun toStored(trip: Trip) = StoredTrip(
        id = trip.id.toString(),
        name = trip.name,
        startDate = trip.startDate.toString(),
        endDate = trip.endDateInclusive.toString(),
        accountIds = trip.accountIds.map(UUID::toString),
        currency = trip.currency,
    )

    private fun fromStored(stored: StoredTrip): Trip? = runCatching {
        Trip(
            id = UUID.fromString(stored.id),
            name = stored.name,
            startDate = LocalDate.parse(stored.startDate),
            endDateInclusive = LocalDate.parse(stored.endDate),
            accountIds = stored.accountIds.mapNotNull {
                runCatching { UUID.fromString(it) }.getOrNull()
            },
            currency = stored.currency,
        )
    }.getOrNull()
}
