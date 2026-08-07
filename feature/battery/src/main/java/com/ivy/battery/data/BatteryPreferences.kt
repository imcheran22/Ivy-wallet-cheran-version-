package com.ivy.battery.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How the raw `BATTERY_PROPERTY_CURRENT_NOW` value should be interpreted.
 * The platform documents µA but a fair number of OEMs report mA instead, so
 * the reader guesses by default and the user can pin it down when the guess
 * is wrong for their device.
 */
enum class CurrentUnitMode {
    AUTO,
    MICRO_AMPS,
    MILLI_AMPS;

    companion object {
        fun fromName(name: String?): CurrentUnitMode =
            entries.firstOrNull { it.name == name } ?: AUTO
    }
}

data class BatterySettings(
    val monitoringEnabled: Boolean,
    val persistentNotification: Boolean,
    val samplingIntervalSec: Int,
    val chargeAlarmEnabled: Boolean,
    val chargeAlarmLevel: Int,
    val chargeAlarmSound: Boolean,
    val chargeAlarmVibrate: Boolean,
    val lowBatteryAlarmEnabled: Boolean,
    val lowBatteryLevel: Int,
    val temperatureAlarmEnabled: Boolean,
    val temperatureAlarmC: Int,
    val designCapacityOverrideMah: Int,
    val temperatureInFahrenheit: Boolean,
    val currentUnitMode: CurrentUnitMode,
    val invertCurrentSign: Boolean,
    val trackAppDrain: Boolean,
    val retentionDays: Int,
    val minLevelDeltaForCapacity: Int,
) {
    companion object {
        val Default = BatterySettings(
            monitoringEnabled = true,
            persistentNotification = true,
            samplingIntervalSec = 30,
            chargeAlarmEnabled = true,
            chargeAlarmLevel = 80,
            chargeAlarmSound = true,
            chargeAlarmVibrate = true,
            lowBatteryAlarmEnabled = true,
            lowBatteryLevel = 15,
            temperatureAlarmEnabled = true,
            temperatureAlarmC = 42,
            designCapacityOverrideMah = 0,
            temperatureInFahrenheit = false,
            currentUnitMode = CurrentUnitMode.AUTO,
            invertCurrentSign = false,
            trackAppDrain = true,
            retentionDays = 30,
            minLevelDeltaForCapacity = 20,
        )
    }
}

private val Context.batteryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ivy_battery_settings"
)

@Singleton
class BatteryPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val MONITORING = booleanPreferencesKey("battery_monitoring_enabled")
        val NOTIFICATION = booleanPreferencesKey("battery_persistent_notification")
        val SAMPLING_SEC = intPreferencesKey("battery_sampling_interval_sec")
        val CHARGE_ALARM = booleanPreferencesKey("battery_charge_alarm_enabled")
        val CHARGE_ALARM_LEVEL = intPreferencesKey("battery_charge_alarm_level")
        val CHARGE_ALARM_SOUND = booleanPreferencesKey("battery_charge_alarm_sound")
        val CHARGE_ALARM_VIBRATE = booleanPreferencesKey("battery_charge_alarm_vibrate")
        val LOW_ALARM = booleanPreferencesKey("battery_low_alarm_enabled")
        val LOW_ALARM_LEVEL = intPreferencesKey("battery_low_alarm_level")
        val TEMP_ALARM = booleanPreferencesKey("battery_temp_alarm_enabled")
        val TEMP_ALARM_C = intPreferencesKey("battery_temp_alarm_c")
        val DESIGN_CAPACITY = intPreferencesKey("battery_design_capacity_override_mah")
        val FAHRENHEIT = booleanPreferencesKey("battery_temperature_fahrenheit")
        val CURRENT_UNIT = stringPreferencesKey("battery_current_unit_mode")
        val INVERT_CURRENT = booleanPreferencesKey("battery_invert_current_sign")
        val TRACK_APPS = booleanPreferencesKey("battery_track_app_drain")
        val RETENTION_DAYS = intPreferencesKey("battery_retention_days")
        val MIN_LEVEL_DELTA = intPreferencesKey("battery_min_level_delta_for_capacity")
    }

    val settings: Flow<BatterySettings> = context.batteryDataStore.data.map { prefs ->
        val defaults = BatterySettings.Default
        BatterySettings(
            monitoringEnabled = prefs[Keys.MONITORING] ?: defaults.monitoringEnabled,
            persistentNotification = prefs[Keys.NOTIFICATION] ?: defaults.persistentNotification,
            samplingIntervalSec = prefs[Keys.SAMPLING_SEC] ?: defaults.samplingIntervalSec,
            chargeAlarmEnabled = prefs[Keys.CHARGE_ALARM] ?: defaults.chargeAlarmEnabled,
            chargeAlarmLevel = prefs[Keys.CHARGE_ALARM_LEVEL] ?: defaults.chargeAlarmLevel,
            chargeAlarmSound = prefs[Keys.CHARGE_ALARM_SOUND] ?: defaults.chargeAlarmSound,
            chargeAlarmVibrate = prefs[Keys.CHARGE_ALARM_VIBRATE] ?: defaults.chargeAlarmVibrate,
            lowBatteryAlarmEnabled = prefs[Keys.LOW_ALARM] ?: defaults.lowBatteryAlarmEnabled,
            lowBatteryLevel = prefs[Keys.LOW_ALARM_LEVEL] ?: defaults.lowBatteryLevel,
            temperatureAlarmEnabled = prefs[Keys.TEMP_ALARM] ?: defaults.temperatureAlarmEnabled,
            temperatureAlarmC = prefs[Keys.TEMP_ALARM_C] ?: defaults.temperatureAlarmC,
            designCapacityOverrideMah = prefs[Keys.DESIGN_CAPACITY]
                ?: defaults.designCapacityOverrideMah,
            temperatureInFahrenheit = prefs[Keys.FAHRENHEIT] ?: defaults.temperatureInFahrenheit,
            currentUnitMode = CurrentUnitMode.fromName(prefs[Keys.CURRENT_UNIT]),
            invertCurrentSign = prefs[Keys.INVERT_CURRENT] ?: defaults.invertCurrentSign,
            trackAppDrain = prefs[Keys.TRACK_APPS] ?: defaults.trackAppDrain,
            retentionDays = prefs[Keys.RETENTION_DAYS] ?: defaults.retentionDays,
            minLevelDeltaForCapacity = prefs[Keys.MIN_LEVEL_DELTA]
                ?: defaults.minLevelDeltaForCapacity,
        )
    }

    suspend fun current(): BatterySettings = settings.first()

    suspend fun setMonitoringEnabled(enabled: Boolean) = put(Keys.MONITORING, enabled)

    suspend fun setPersistentNotification(enabled: Boolean) = put(Keys.NOTIFICATION, enabled)

    suspend fun setSamplingIntervalSec(seconds: Int) =
        put(Keys.SAMPLING_SEC, seconds.coerceIn(MIN_SAMPLING_SEC, MAX_SAMPLING_SEC))

    suspend fun setChargeAlarmEnabled(enabled: Boolean) = put(Keys.CHARGE_ALARM, enabled)

    suspend fun setChargeAlarmLevel(level: Int) =
        put(Keys.CHARGE_ALARM_LEVEL, level.coerceIn(MIN_ALARM_LEVEL, MAX_LEVEL))

    suspend fun setChargeAlarmSound(enabled: Boolean) = put(Keys.CHARGE_ALARM_SOUND, enabled)

    suspend fun setChargeAlarmVibrate(enabled: Boolean) = put(Keys.CHARGE_ALARM_VIBRATE, enabled)

    suspend fun setLowBatteryAlarmEnabled(enabled: Boolean) = put(Keys.LOW_ALARM, enabled)

    suspend fun setLowBatteryLevel(level: Int) =
        put(Keys.LOW_ALARM_LEVEL, level.coerceIn(1, MAX_LOW_ALARM_LEVEL))

    suspend fun setTemperatureAlarmEnabled(enabled: Boolean) = put(Keys.TEMP_ALARM, enabled)

    suspend fun setTemperatureAlarmC(celsius: Int) =
        put(Keys.TEMP_ALARM_C, celsius.coerceIn(MIN_TEMP_ALARM_C, MAX_TEMP_ALARM_C))

    suspend fun setDesignCapacityOverride(mah: Int) =
        put(Keys.DESIGN_CAPACITY, mah.coerceIn(0, MAX_DESIGN_CAPACITY))

    suspend fun setTemperatureInFahrenheit(enabled: Boolean) = put(Keys.FAHRENHEIT, enabled)

    suspend fun setCurrentUnitMode(mode: CurrentUnitMode) = put(Keys.CURRENT_UNIT, mode.name)

    suspend fun setInvertCurrentSign(enabled: Boolean) = put(Keys.INVERT_CURRENT, enabled)

    suspend fun setTrackAppDrain(enabled: Boolean) = put(Keys.TRACK_APPS, enabled)

    suspend fun setRetentionDays(days: Int) =
        put(Keys.RETENTION_DAYS, days.coerceIn(MIN_RETENTION_DAYS, MAX_RETENTION_DAYS))

    suspend fun setMinLevelDeltaForCapacity(delta: Int) =
        put(Keys.MIN_LEVEL_DELTA, delta.coerceIn(MIN_CAPACITY_DELTA, MAX_CAPACITY_DELTA))

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.batteryDataStore.edit { it[key] = value }
    }

    companion object {
        const val MIN_SAMPLING_SEC = 5
        const val MAX_SAMPLING_SEC = 300
        const val MIN_ALARM_LEVEL = 30
        const val MAX_LEVEL = 100
        const val MAX_LOW_ALARM_LEVEL = 50
        const val MIN_TEMP_ALARM_C = 30
        const val MAX_TEMP_ALARM_C = 60
        const val MAX_DESIGN_CAPACITY = 30_000
        const val MIN_RETENTION_DAYS = 1
        const val MAX_RETENTION_DAYS = 365
        const val MIN_CAPACITY_DELTA = 5
        const val MAX_CAPACITY_DELTA = 60
    }
}
