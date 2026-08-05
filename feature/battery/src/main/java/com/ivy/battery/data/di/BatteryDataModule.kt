package com.ivy.battery.data.di

import android.content.Context
import com.ivy.battery.data.db.BatteryDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BatteryDataModule {

    @Provides
    @Singleton
    fun provideBatteryDatabase(
        @ApplicationContext appContext: Context,
    ): BatteryDatabase = BatteryDatabase.create(appContext)
}
