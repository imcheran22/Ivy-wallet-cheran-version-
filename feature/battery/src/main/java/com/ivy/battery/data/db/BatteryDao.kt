package com.ivy.battery.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BatterySampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sample: BatterySampleEntity)

    @Query("SELECT * FROM battery_samples WHERE timestamp >= :from ORDER BY timestamp ASC")
    suspend fun since(from: Long): List<BatterySampleEntity>

    @Query("SELECT * FROM battery_samples WHERE timestamp >= :from ORDER BY timestamp ASC")
    fun sinceFlow(from: Long): Flow<List<BatterySampleEntity>>

    @Query(
        "SELECT * FROM battery_samples WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp ASC"
    )
    suspend fun between(from: Long, to: Long): List<BatterySampleEntity>

    @Query("SELECT * FROM battery_samples ORDER BY timestamp DESC LIMIT 1")
    suspend fun latest(): BatterySampleEntity?

    @Query("SELECT COUNT(*) FROM battery_samples")
    fun countFlow(): Flow<Int>

    @Query("DELETE FROM battery_samples WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM battery_samples")
    suspend fun deleteAll()
}

@Dao
interface ChargeSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: ChargeSessionEntity)

    @Query("SELECT * FROM charge_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun openSession(): ChargeSessionEntity?

    @Query("SELECT * FROM charge_sessions ORDER BY startedAt DESC")
    fun allFlow(): Flow<List<ChargeSessionEntity>>

    @Query("SELECT * FROM charge_sessions ORDER BY startedAt DESC LIMIT :limit")
    fun recentFlow(limit: Int): Flow<List<ChargeSessionEntity>>

    @Query("SELECT * FROM charge_sessions ORDER BY startedAt DESC")
    suspend fun all(): List<ChargeSessionEntity>

    @Query("DELETE FROM charge_sessions WHERE endedAt IS NOT NULL AND endedAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM charge_sessions")
    suspend fun deleteAll()
}

@Dao
interface DischargeSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: DischargeSessionEntity)

    @Query("SELECT * FROM discharge_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun openSession(): DischargeSessionEntity?

    @Query("SELECT * FROM discharge_sessions ORDER BY startedAt DESC")
    fun allFlow(): Flow<List<DischargeSessionEntity>>

    @Query("SELECT * FROM discharge_sessions ORDER BY startedAt DESC LIMIT :limit")
    fun recentFlow(limit: Int): Flow<List<DischargeSessionEntity>>

    @Query("SELECT * FROM discharge_sessions ORDER BY startedAt DESC")
    suspend fun all(): List<DischargeSessionEntity>

    @Query("DELETE FROM discharge_sessions WHERE endedAt IS NOT NULL AND endedAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM discharge_sessions")
    suspend fun deleteAll()
}

@Dao
interface AppDrainDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<AppDrainEntity>)

    @Query("DELETE FROM app_drain WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)

    @Query(
        """
        SELECT packageName,
               SUM(foregroundMs) AS foregroundMs,
               SUM(estimatedMah) AS estimatedMah,
               SUM(estimatedPercent) AS estimatedPercent
        FROM app_drain
        WHERE updatedAt >= :from
        GROUP BY packageName
        ORDER BY estimatedMah DESC
        """
    )
    fun aggregatedSinceFlow(from: Long): Flow<List<AppDrainAggregate>>

    @Query("DELETE FROM app_drain WHERE updatedAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM app_drain")
    suspend fun deleteAll()
}

data class AppDrainAggregate(
    val packageName: String,
    val foregroundMs: Long,
    val estimatedMah: Double,
    val estimatedPercent: Double,
)
