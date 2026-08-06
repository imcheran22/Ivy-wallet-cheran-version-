package com.ivy.battery.domain

import android.content.Context
import com.ivy.battery.data.BatteryPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The battery's *design* capacity in mAh - the number the manufacturer printed
 * on the spec sheet. It is not part of the public SDK, so it's read out of the
 * hidden `PowerProfile` the framework itself uses for its battery estimates.
 *
 * Resolution order:
 *  1. an explicit user override (always wins - the reflection can be wrong),
 *  2. `com.android.internal.os.PowerProfile#getBatteryCapacity`,
 *  3. the `config_batteryCapacity` framework resource,
 *  4. nothing, in which case health is reported as "not available".
 */
@Singleton
class DesignCapacityProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: BatteryPreferences,
) {
    @Volatile
    private var cachedFromSystem: Double? = null

    @Volatile
    private var systemLookupDone: Boolean = false

    suspend fun designCapacityMah(): Double? {
        val override = preferences.current().designCapacityOverrideMah
        if (override > 0) return override.toDouble()
        return systemDesignCapacityMah()
    }

    fun systemDesignCapacityMah(): Double? {
        if (systemLookupDone) return cachedFromSystem
        val value = readFromPowerProfile() ?: readFromFrameworkResource()
        cachedFromSystem = value?.takeIf { it > MIN_PLAUSIBLE_MAH && it < MAX_PLAUSIBLE_MAH }
        systemLookupDone = true
        return cachedFromSystem
    }

    private fun readFromPowerProfile(): Double? = runCatching {
        val powerProfileClass = Class.forName(POWER_PROFILE_CLASS)
        val instance = powerProfileClass
            .getConstructor(Context::class.java)
            .newInstance(context)
        powerProfileClass
            .getMethod("getBatteryCapacity")
            .invoke(instance) as? Double
    }.onFailure {
        Timber.d("Battery: PowerProfile capacity unavailable (${it.message})")
    }.getOrNull()

    private fun readFromFrameworkResource(): Double? = runCatching {
        val resId = context.resources.getIdentifier(
            "config_batteryCapacity",
            "integer",
            "android",
        )
        if (resId == 0) null else context.resources.getInteger(resId).toDouble()
    }.getOrNull()

    companion object {
        private const val POWER_PROFILE_CLASS = "com.android.internal.os.PowerProfile"
        private const val MIN_PLAUSIBLE_MAH = 100.0
        private const val MAX_PLAUSIBLE_MAH = 30_000.0
    }
}
