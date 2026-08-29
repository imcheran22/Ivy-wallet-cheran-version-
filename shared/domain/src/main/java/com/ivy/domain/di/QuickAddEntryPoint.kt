package com.ivy.domain.di

import com.ivy.domain.AppStarter
import com.ivy.domain.usecase.quickadd.QuickAddOptionsUseCase
import com.ivy.domain.usecase.quickadd.QuickAddPresetStore
import com.ivy.domain.usecase.quickadd.QuickAddTransactionUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Reaches the quick-add machinery from places Hilt can't inject into: Glance action callbacks,
 * which are instantiated by the widget host rather than by us.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface QuickAddEntryPoint {
    fun quickAddTransactionUseCase(): QuickAddTransactionUseCase
    fun quickAddPresetStore(): QuickAddPresetStore
    fun quickAddOptionsUseCase(): QuickAddOptionsUseCase
    fun appStarter(): AppStarter
}
