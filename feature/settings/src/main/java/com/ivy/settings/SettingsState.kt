package com.ivy.settings

import com.ivy.base.legacy.Theme

data class SettingsState(
    val currencyCode: String,
    val name: String,
    val currentTheme: Theme,
    val lockApp: Boolean,
    val showNotifications: Boolean,
    val hideCurrentBalance: Boolean,
    val hideIncome: Boolean,
    val treatTransfersAsIncomeExpense: Boolean,
    val startDateOfMonth: String,
    val progressState: Boolean,
    val languageOptionVisible: Boolean,
    val cloudSyncEnabled: Boolean = false,
    val cloudSyncSupabaseUrl: String = "",
    val cloudSyncSupabaseAnonKey: String = "",
    val cloudSyncInProgress: Boolean = false,
    val cloudSyncLastSyncedEpochMs: Long? = null,
    val cloudSyncError: String? = null,
)
