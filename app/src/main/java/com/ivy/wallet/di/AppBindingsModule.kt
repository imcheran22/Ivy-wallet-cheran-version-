package com.ivy.wallet.di

import com.ivy.domain.AppStarter
import com.ivy.domain.sync.CloudSyncTrigger
import com.ivy.wallet.IvyAppStarter
import com.ivy.wallet.sync.CloudSyncSchedulerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindingsModule {
    @Binds
    abstract fun appStarter(appStarter: IvyAppStarter): AppStarter

    @Binds
    abstract fun cloudSyncTrigger(scheduler: CloudSyncSchedulerImpl): CloudSyncTrigger
}
