package com.ivy.notifmirror.data

import android.content.Context
import com.ivy.base.CoupleTransactionSyncer
import com.ivy.notifmirror.sync.CoupleTransactionSyncerImpl
import com.ivy.notifmirror.sync.PartnerTransactionStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MirrorDataModule {

    @Provides
    @Singleton
    fun provideMirrorPrefs(
        @ApplicationContext context: Context,
    ): MirrorPrefs = MirrorPrefs(context)

    @Provides
    @Singleton
    fun providePartnerTransactionStore(
        @ApplicationContext context: Context,
    ): PartnerTransactionStore = PartnerTransactionStore(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MirrorBindingsModule {

    @Binds
    @Singleton
    abstract fun bindCoupleTransactionSyncer(
        impl: CoupleTransactionSyncerImpl,
    ): CoupleTransactionSyncer
}
