package com.ivy.notifmirror.data

import android.content.Context
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
}
