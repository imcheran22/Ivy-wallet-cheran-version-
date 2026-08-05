package com.ivy.battery.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Battery telemetry lives in its own database file: it's high-churn,
 * disposable data that has nothing to do with the user's financial records,
 * and keeping it separate means it can never affect an Ivy Wallet migration.
 */
@Database(
    entities = [
        BatterySampleEntity::class,
        ChargeSessionEntity::class,
        DischargeSessionEntity::class,
        AppDrainEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class BatteryDatabase : RoomDatabase() {
    abstract val sampleDao: BatterySampleDao
    abstract val chargeSessionDao: ChargeSessionDao
    abstract val dischargeSessionDao: DischargeSessionDao
    abstract val appDrainDao: AppDrainDao

    companion object {
        private const val DB_NAME = "ivy-battery.db"

        fun create(applicationContext: Context): BatteryDatabase =
            Room.databaseBuilder(applicationContext, BatteryDatabase::class.java, DB_NAME)
                .fallbackToDestructiveMigration()
                .build()
    }
}
