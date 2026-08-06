package com.ivy.battery.service

import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ivy.battery.data.BatteryPreferences
import com.ivy.battery.data.BatteryRepository
import com.ivy.battery.data.BatterySettings
import com.ivy.battery.domain.BatteryReader
import com.ivy.battery.domain.model.BatterySnapshot
import com.ivy.battery.domain.model.TimeEstimates
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

/**
 * Keeps sampling the battery while the app is in the background - which is the
 * whole point, since a charge measurement is worthless if it stops the moment
 * the user locks the screen.
 *
 * It samples on a timer *and* on every relevant system broadcast, but only
 * persists a row when something actually changed, so the database doesn't fill
 * up with identical readings.
 */
@AndroidEntryPoint
class BatteryMonitorService : Service() {

    @Inject
    lateinit var reader: BatteryReader

    @Inject
    lateinit var repository: BatteryRepository

    @Inject
    lateinit var preferences: BatteryPreferences

    @Inject
    lateinit var notifications: BatteryNotifications

    @Inject
    lateinit var alarmPlayer: BatteryAlarmPlayer

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var samplingJob: Job? = null

    private val wakeUps = MutableSharedFlow<Intent?>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private var lastPersistedAt = 0L
    private var lastPersistedLevel = -1
    private var lastPersistedScreenOn: Boolean? = null
    private var lastPersistedCharging: Boolean? = null
    private var lastPrunedAt = 0L

    private var chargeAlarmActive = false
    private var lowAlarmActive = false
    private var temperatureAlarmActive = false
    private var alarmTimeoutJob: Job? = null

    private var cachedEstimates: TimeEstimates? = null

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val batteryIntent = intent?.takeIf { it.action == Intent.ACTION_BATTERY_CHANGED }
            wakeUps.tryEmit(batteryIntent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifications.createChannels()
        startAsForeground(
            notifications.statusNotification(
                snapshot = null,
                estimates = null,
                detailed = true,
                fahrenheit = false,
            )
        )
        registerSystemReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoring()
                return START_NOT_STICKY
            }

            ACTION_STOP_ALARM -> {
                silenceAlarms()
                return START_STICKY
            }

            else -> startSampling()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        alarmPlayer.stop()
        runCatching { unregisterReceiver(systemReceiver) }
        scope.cancel()
        super.onDestroy()
    }

    private fun registerSystemReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(
            this,
            systemReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun startSampling() {
        if (samplingJob?.isActive == true) return
        samplingJob = scope.launch {
            var pending: Intent? = null
            while (isActive) {
                val settings = runCatching { preferences.current() }
                    .getOrDefault(BatterySettings.Default)

                if (!settings.monitoringEnabled) {
                    stopMonitoring()
                    return@launch
                }

                runCatching { tick(pending, settings) }
                    .onFailure { Timber.w(it, "Battery: sampling tick failed") }

                pending = withTimeoutOrNull(settings.samplingIntervalSec * 1000L) {
                    wakeUps.first()
                }
            }
        }
    }

    private suspend fun tick(batteryIntent: Intent?, settings: BatterySettings) {
        val snapshot = reader.read(
            broadcast = batteryIntent,
            unitMode = settings.currentUnitMode,
            invertSign = settings.invertCurrentSign,
        )

        if (shouldPersist(snapshot)) {
            repository.record(snapshot)
            lastPersistedAt = snapshot.timestamp
            lastPersistedLevel = snapshot.level
            lastPersistedScreenOn = snapshot.screenOn
            lastPersistedCharging = snapshot.charging
            cachedEstimates = repository.estimates(snapshot, settings.chargeAlarmLevel)
            maybePrune()
        }

        checkAlarms(snapshot, settings)

        notifications.updateStatus(
            notifications.statusNotification(
                snapshot = snapshot,
                estimates = cachedEstimates,
                detailed = settings.persistentNotification,
                fahrenheit = settings.temperatureInFahrenheit,
            )
        )
    }

    /**
     * `ACTION_BATTERY_CHANGED` fires far more often than anything interesting
     * happens, so a reading is only stored when it carries new information.
     */
    private fun shouldPersist(snapshot: BatterySnapshot): Boolean =
        snapshot.level != lastPersistedLevel ||
                snapshot.screenOn != lastPersistedScreenOn ||
                snapshot.charging != lastPersistedCharging ||
                snapshot.timestamp - lastPersistedAt >= MIN_PERSIST_GAP_MS

    private suspend fun maybePrune() {
        val now = System.currentTimeMillis()
        if (now - lastPrunedAt < PRUNE_INTERVAL_MS) return
        lastPrunedAt = now
        runCatching { repository.pruneOldData() }
            .onFailure { Timber.w(it, "Battery: pruning failed") }
    }

    private fun checkAlarms(snapshot: BatterySnapshot, settings: BatterySettings) {
        checkChargeAlarm(snapshot, settings)
        checkLowBatteryAlarm(snapshot, settings)
        checkTemperatureAlarm(snapshot, settings)

        if (!chargeAlarmActive && !lowAlarmActive && !temperatureAlarmActive) {
            alarmPlayer.stop()
        }
    }

    private fun checkChargeAlarm(snapshot: BatterySnapshot, settings: BatterySettings) {
        val shouldFire = settings.chargeAlarmEnabled &&
                snapshot.charging &&
                snapshot.level >= settings.chargeAlarmLevel

        when {
            shouldFire && !chargeAlarmActive -> {
                chargeAlarmActive = true
                fireAlarm(
                    id = BatteryNotifications.ALARM_ID_CHARGE,
                    title = "Battery at ${snapshot.level}%",
                    message = "Unplug the charger. Staying above " +
                            "${settings.chargeAlarmLevel}% is what wears a " +
                            "lithium-ion cell out fastest.",
                    settings = settings,
                )
            }

            !shouldFire && chargeAlarmActive &&
                    (!snapshot.charging || snapshot.level < settings.chargeAlarmLevel) -> {
                chargeAlarmActive = false
            }
        }
    }

    private fun checkLowBatteryAlarm(snapshot: BatterySnapshot, settings: BatterySettings) {
        val shouldFire = settings.lowBatteryAlarmEnabled &&
                !snapshot.charging &&
                snapshot.level <= settings.lowBatteryLevel

        when {
            shouldFire && !lowAlarmActive -> {
                lowAlarmActive = true
                fireAlarm(
                    id = BatteryNotifications.ALARM_ID_LOW,
                    title = "Battery low: ${snapshot.level}%",
                    message = "Plug in soon. Deep discharges below " +
                            "${settings.lowBatteryLevel}% also add wear.",
                    settings = settings,
                )
            }

            !shouldFire && lowAlarmActive -> lowAlarmActive = false
        }
    }

    private fun checkTemperatureAlarm(snapshot: BatterySnapshot, settings: BatterySettings) {
        val shouldFire = settings.temperatureAlarmEnabled &&
                snapshot.temperatureC >= settings.temperatureAlarmC

        when {
            shouldFire && !temperatureAlarmActive -> {
                temperatureAlarmActive = true
                fireAlarm(
                    id = BatteryNotifications.ALARM_ID_TEMPERATURE,
                    title = "Battery is hot: %.1f°C".format(snapshot.temperatureC),
                    message = "Heat is the second-biggest cause of capacity " +
                            "loss. Unplug, stop heavy apps, and let it cool down.",
                    settings = settings,
                )
            }

            temperatureAlarmActive &&
                    snapshot.temperatureC < settings.temperatureAlarmC - TEMP_HYSTERESIS_C ->
                temperatureAlarmActive = false
        }
    }

    private fun fireAlarm(id: Int, title: String, message: String, settings: BatterySettings) {
        notifications.showAlarm(id, title, message, withSound = settings.chargeAlarmSound)
        alarmPlayer.start(
            sound = settings.chargeAlarmSound,
            vibrate = settings.chargeAlarmVibrate,
        )
        alarmTimeoutJob?.cancel()
        alarmTimeoutJob = scope.launch {
            delay(ALARM_MAX_DURATION_MS)
            alarmPlayer.stop()
        }
    }

    private fun silenceAlarms() {
        alarmTimeoutJob?.cancel()
        alarmPlayer.stop()
        notifications.cancelAlarms()
    }

    private fun stopMonitoring() {
        silenceAlarms()
        samplingJob?.cancel()
        samplingJob = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startAsForeground(notification: Notification) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    BatteryNotifications.STATUS_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                @Suppress("DEPRECATION")
                startForeground(BatteryNotifications.STATUS_ID, notification)
            }
        }.onFailure {
            // Android 12+ refuses foreground starts from the background; the
            // keep-alive worker will retry the next time the app is in front.
            Timber.w(it, "Battery: could not start the monitor in the foreground")
            stopSelf()
        }
    }

    companion object {
        const val ACTION_START = "com.ivy.battery.action.START"
        const val ACTION_STOP = "com.ivy.battery.action.STOP"
        const val ACTION_STOP_ALARM = "com.ivy.battery.action.STOP_ALARM"

        private const val MIN_PERSIST_GAP_MS = 15_000L
        private const val PRUNE_INTERVAL_MS = 6L * 60L * 60L * 1000L
        private const val ALARM_MAX_DURATION_MS = 2L * 60L * 1000L
        private const val TEMP_HYSTERESIS_C = 2
    }
}
