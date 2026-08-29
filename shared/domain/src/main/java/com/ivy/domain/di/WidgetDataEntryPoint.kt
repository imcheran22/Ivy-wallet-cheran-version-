package com.ivy.domain.di

import com.ivy.domain.AppStarter
import com.ivy.domain.usecase.budget.BudgetProgressUseCase
import com.ivy.domain.usecase.recent.RecentTransactionsUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Data for the read-only widgets, reached from Glance where Hilt can't inject.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetDataEntryPoint {
    fun budgetProgressUseCase(): BudgetProgressUseCase
    fun recentTransactionsUseCase(): RecentTransactionsUseCase
    fun appStarter(): AppStarter
}
