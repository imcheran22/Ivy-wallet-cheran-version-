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
    val smsAutoImportEnabled: Boolean = false,
    val quickAddNotificationEnabled: Boolean = false,
    val cloudSyncEnabled: Boolean = false,
    val cloudSyncSupabaseUrl: String = "",
    val cloudSyncSupabaseAnonKey: String = "",
    val cloudSyncInProgress: Boolean = false,
    val cloudSyncLastSyncedEpochMs: Long? = null,
    val cloudSyncError: String? = null,
    val smsCapture: SmsCaptureSummary = SmsCaptureSummary(),
)

/**
 * A plain-language answer to "is auto-capture actually working?".
 *
 * Capture runs in the background, so the only evidence a user ever gets is the transactions
 * that appear - and when nothing appears there is no way to tell a quiet week from a broken
 * permission. These fields exist so the screen can say which of the two it is.
 */
data class SmsCaptureSummary(
    val enabled: Boolean = false,
    val permissionGranted: Boolean = false,
    val capturedTotal: Int = 0,
    val lastCaptureAtEpochMs: Long? = null,
    val lastSweepAtEpochMs: Long? = null,
    val lastSweepSummary: String? = null,
    val sweeping: Boolean = false,
    /** Nothing received before this is imported. Null until capture has been switched on. */
    val importFromEpochMs: Long? = null,
)
