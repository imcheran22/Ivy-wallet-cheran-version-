package com.ivy.wallet.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import java.io.File
import java.security.MessageDigest

object AppIntegrityChecker {

    fun isDebuggerAttached(): Boolean {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger()
    }

    fun isRunningOnEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.BOARD == "QC_Reference_Phone"
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.HOST.startsWith("Build")
                || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                || "google_sdk" == Build.PRODUCT)
    }

    fun isDeviceRooted(): Boolean {
        val rootPaths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su",
            "/su/bin",
            "/system/xbin/daemonsu",
        )
        return rootPaths.any { File(it).exists() }
    }

    fun isAppDebuggable(context: Context): Boolean {
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    enum class InstallSource {
        GOOGLE_PLAY,
        GOOGLE_DRIVE,
        PACKAGE_INSTALLER,
        FILE_MANAGER,
        ADB_SIDELOAD,
        UNKNOWN
    }

    fun getInstallSource(context: Context): InstallSource {
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(context.packageName)
        }

        return when (installer) {
            "com.android.vending" -> InstallSource.GOOGLE_PLAY
            "com.google.android.apps.docs" -> InstallSource.GOOGLE_DRIVE
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.google.android.apps.nbu.files",
            "com.samsung.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.coloros.filemanager" -> InstallSource.PACKAGE_INSTALLER
            "com.google.android.gm" -> InstallSource.FILE_MANAGER
            null -> InstallSource.ADB_SIDELOAD
            else -> InstallSource.UNKNOWN
        }
    }

    fun isInstalledFromAllowedSource(context: Context): Boolean {
        return when (getInstallSource(context)) {
            InstallSource.GOOGLE_PLAY,
            InstallSource.GOOGLE_DRIVE,
            InstallSource.PACKAGE_INSTALLER,
            InstallSource.FILE_MANAGER -> true
            InstallSource.ADB_SIDELOAD -> true
            InstallSource.UNKNOWN -> false
        }
    }

    @Suppress("DEPRECATION")
    fun getSigningCertHash(context: Context): String? {
        return try {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                ).signingInfo
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                ).signatures
            }

            signatures?.firstOrNull()?.let { sig ->
                val md = MessageDigest.getInstance("SHA-256")
                md.update(sig.toByteArray())
                md.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun verifySignature(context: Context, expectedHash: String): Boolean {
        val currentHash = getSigningCertHash(context) ?: return false
        return currentHash.equals(expectedHash, ignoreCase = true)
    }

    fun isAppTampered(context: Context): Boolean {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName, 0
            )
            val appFile = File(packageInfo.applicationInfo.sourceDir)
            !appFile.exists() || appFile.length() == 0L
        } catch (e: Exception) {
            true
        }
    }
}
