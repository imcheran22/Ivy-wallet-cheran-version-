package com.ivy.battery.domain

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import com.ivy.battery.data.BatteryPreferences
import com.ivy.battery.data.CurrentUnitMode
import com.ivy.battery.domain.model.BatterySnapshot
import com.ivy.battery.domain.model.ChargeStatus
import com.ivy.battery.domain.model.PlugType
import com.ivy.battery.domain.model.ReportedHealth
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Reads every battery metric the platform exposes into a single [BatterySnapshot].
 *
 * Two things are messier than the docs suggest and are normalised here:
 *  - the unit of `BATTERY_PROPERTY_CURRENT_NOW` (documented µA, sometimes mA),
 *  - its sign (documented positive-when-charging, inverted on some OEMs).
 */
@Singleton
class BatteryReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: BatteryPreferences,
) {
    private val batteryManager: BatteryManager? =
        context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    private val powerManager: PowerManager? =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    suspend fun read(broadcast: Intent? = null): BatterySnapshot {
        val settings = preferences.current()
        return read(
            broadcast = broadcast,
            unitMode = settings.currentUnitMode,
            invertSign = settings.invertCurrentSign,
        )
    }

    fun read(
        broadcast: Intent?,
        unitMode: CurrentUnitMode,
        invertSign: Boolean,
    ): BatterySnapshot {
        val intent = broadcast ?: stickyBatteryIntent()
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, DEFAULT_SCALE) ?: DEFAULT_SCALE
        val rawLevel = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val status = ChargeStatus.from(
            intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        )

        val level = when {
            rawLevel >= 0 && scale > 0 -> (rawLevel * DEFAULT_SCALE / scale)
            else -> capacityProperty() ?: 0
        }

        return BatterySnapshot(
            timestamp = System.currentTimeMillis(),
            level = level.coerceIn(0, DEFAULT_SCALE),
            scale = scale,
            status = status,
            plugType = PlugType.from(
                intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            ),
            reportedHealth = ReportedHealth.from(
                intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
            ),
            technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY).orEmpty()
                .ifBlank { "Unknown" },
            temperatureC = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
            voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0,
            currentMa = normalizeCurrent(
                raw = intProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
                status = status,
                unitMode = unitMode,
                invertSign = invertSign,
            ),
            averageCurrentMa = intProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
                ?.let {
                    normalizeCurrent(it, status, unitMode, invertSign)
                },
            chargeCounterUah = intProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                ?.takeIf { it > 0 },
            energyCounterNwh = longProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
                ?.takeIf { it > 0 },
            present = intent?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true) ?: true,
            screenOn = powerManager?.isInteractive ?: true,
            powerSaveMode = powerManager?.isPowerSaveMode ?: false,
            uptimeMs = SystemClock.uptimeMillis(),
            elapsedRealtimeMs = SystemClock.elapsedRealtime(),
        )
    }

    fun stickyBatteryIntent(): Intent? = runCatching {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }.getOrNull()

    private fun capacityProperty(): Int? = intProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    private fun intProperty(id: Int): Int? {
        val value = batteryManager?.getIntProperty(id) ?: return null
        // The platform returns Integer.MIN_VALUE for "not supported".
        return value.takeIf { it != Int.MIN_VALUE && it != Int.MAX_VALUE }
    }

    private fun longProperty(id: Int): Long? {
        val value = batteryManager?.getLongProperty(id) ?: return null
        return value.takeIf { it != Long.MIN_VALUE && it != Long.MAX_VALUE }
    }

    /**
     * Converts the raw reading to mA and orients it so that positive always
     * means "energy going into the battery".
     */
    private fun normalizeCurrent(
        raw: Int?,
        status: ChargeStatus,
        unitMode: CurrentUnitMode,
        invertSign: Boolean,
    ): Double {
        if (raw == null || raw == 0) return 0.0

        val inMilliAmps = when (unitMode) {
            CurrentUnitMode.MILLI_AMPS -> raw.toDouble()
            CurrentUnitMode.MICRO_AMPS -> raw / MICRO_PER_MILLI
            // A phone never really draws 10 A, so anything that large has to be µA.
            CurrentUnitMode.AUTO ->
                if (abs(raw) >= MICRO_AMP_THRESHOLD) raw / MICRO_PER_MILLI else raw.toDouble()
        }

        val signed = if (invertSign) -inMilliAmps else inMilliAmps

        // Some OEMs report an unsigned magnitude; trust the charge status over the sign.
        return when {
            status == ChargeStatus.CHARGING && signed < 0 -> -signed
            status == ChargeStatus.DISCHARGING && signed > 0 -> -signed
            else -> signed
        }
    }

    /**
     * True when this device reports a usable coulomb counter. Without one we
     * fall back to integrating current over time, which is noticeably noisier.
     */
    fun hasChargeCounter(): Boolean =
        intProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.let { it > 0 } == true

    fun isBatteryOptimizationIgnored(): Boolean = runCatching {
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }.getOrDefault(false)

    companion object {
        private const val DEFAULT_SCALE = 100
        private const val MICRO_PER_MILLI = 1000.0
        private const val MICRO_AMP_THRESHOLD = 10_000
    }
}
