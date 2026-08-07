package com.ivy.wallet.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class SecurityStatus(
        val isDebuggerAttached: Boolean,
        val isRooted: Boolean,
        val isDebuggable: Boolean,
        val isFromAllowedSource: Boolean,
        val isEmulator: Boolean,
    ) {
        val hasSecurityThreat: Boolean
            get() = isDebuggerAttached || isDebuggable

        val hasWarnings: Boolean
            get() = isRooted || isEmulator
    }

    fun checkSecurity(): SecurityStatus {
        val status = SecurityStatus(
            isDebuggerAttached = AppIntegrityChecker.isDebuggerAttached(),
            isRooted = AppIntegrityChecker.isDeviceRooted(),
            isDebuggable = AppIntegrityChecker.isAppDebuggable(context),
            isFromAllowedSource = AppIntegrityChecker.isInstalledFromAllowedSource(context),
            isEmulator = AppIntegrityChecker.isRunningOnEmulator(),
        )
        logSecurityStatus(status)
        return status
    }

    fun getSigningCertHash(): String? {
        return AppIntegrityChecker.getSigningCertHash(context)
    }

    private fun logSecurityStatus(status: SecurityStatus) {
        if (status.isDebuggerAttached) {
            Timber.w("Security: Debugger is attached")
        }
        if (status.isRooted) {
            Timber.w("Security: Device appears to be rooted")
        }
        if (status.isDebuggable) {
            Timber.w("Security: App is running in debuggable mode")
        }
        if (!status.isFromAllowedSource) {
            Timber.w("Security: App was not installed from an allowed source")
        }
        if (status.isEmulator) {
            Timber.w("Security: App is running on an emulator")
        }
    }
}
