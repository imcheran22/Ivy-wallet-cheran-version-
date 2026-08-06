package com.ivy.battery.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ivy.battery.data.BatteryPreferences
import com.ivy.battery.data.BatteryRepository
import com.ivy.battery.data.BatterySettings
import com.ivy.battery.data.toDomain
import com.ivy.battery.domain.AppDrainEstimator
import com.ivy.battery.domain.BatteryReader
import com.ivy.battery.domain.model.AppDrain
import com.ivy.battery.domain.model.BatterySnapshot
import com.ivy.battery.domain.model.TimeEstimates
import com.ivy.battery.service.BatteryMonitorController
import com.ivy.ui.ComposeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@Stable
@SuppressLint("StaticFieldLeak")
@HiltViewModel
class BatteryViewModel @Inject constructor(
    private val reader: BatteryReader,
    private val repository: BatteryRepository,
    private val preferences: BatteryPreferences,
    private val appDrainEstimator: AppDrainEstimator,
    private val controller: BatteryMonitorController,
    @ApplicationContext private val context: Context,
) : ComposeViewModel<BatteryUiState, BatteryUiEvent>() {

    private var historyRange by mutableStateOf(HistoryRange.DAY)
    private var usageAccessProbe by mutableIntStateOf(0)

    @Suppress("LongMethod")
    @Composable
    override fun uiState(): BatteryUiState {
        val settingsFlow = remember { preferences.settings }
        val settings by settingsFlow.collectAsState(initial = BatterySettings.Default)

        // Opening the screen is a legitimate foreground moment, which is the
        // one time Android reliably lets us (re)start the monitor service.
        LaunchedEffect(settings.monitoringEnabled) {
            controller.applySettings()
        }

        val snapshot = liveSnapshot(settings)
        val estimates = timeEstimates(snapshot, settings.chargeAlarmLevel)

        val healthFlow = remember { repository.healthFlow() }
        val health by healthFlow.collectAsState(initial = null)

        val chargeFlow = remember { repository.chargeSessionsFlow() }
        val chargeEntities by chargeFlow.collectAsState(initial = emptyList())
        val chargeSessions = remember(chargeEntities) {
            chargeEntities.map { it.toDomain() }.toImmutableList()
        }

        val dischargeFlow = remember { repository.dischargeSessionsFlow() }
        val dischargeEntities by dischargeFlow.collectAsState(initial = emptyList())
        val dischargeSessions = remember(dischargeEntities) {
            dischargeEntities.map { it.toDomain() }.toImmutableList()
        }

        val rangeStart = remember(historyRange) {
            System.currentTimeMillis() - historyRange.durationMs
        }

        val samplesFlow = remember(rangeStart) { repository.samplesSinceFlow(rangeStart) }
        val samples by samplesFlow.collectAsState(initial = emptyList())

        val levelHistory = remember(samples) {
            samples.downsample().map {
                HistoryPoint(it.timestamp, it.level.toFloat(), it.plugged != 0)
            }.toImmutableList()
        }
        val temperatureHistory = remember(samples) {
            samples.downsample().map {
                HistoryPoint(it.timestamp, it.temperatureC, it.plugged != 0)
            }.toImmutableList()
        }
        val currentHistory = remember(samples) {
            samples.downsample().map {
                HistoryPoint(it.timestamp, it.currentMa.toFloat(), it.plugged != 0)
            }.toImmutableList()
        }

        val sampleCountFlow = remember { repository.sampleCountFlow() }
        val sampleCount by sampleCountFlow.collectAsState(initial = 0)

        val designCapacity by produceState<Double?>(initialValue = null) {
            value = repository.systemDesignCapacityMah()
        }

        val hasUsageAccess by produceState(initialValue = false, usageAccessProbe) {
            value = appDrainEstimator.hasUsageAccess()
        }

        return BatteryUiState(
            snapshot = snapshot,
            estimates = estimates,
            health = health,
            ongoingCharge = chargeSessions.firstOrNull { it.ongoing },
            ongoingDischarge = dischargeSessions.firstOrNull { it.ongoing },
            chargeSessions = chargeSessions,
            dischargeSessions = dischargeSessions,
            levelHistory = levelHistory,
            temperatureHistory = temperatureHistory,
            currentHistory = currentHistory,
            appDrain = appDrain(rangeStart),
            hasUsageAccess = hasUsageAccess,
            settings = settings,
            historyRange = historyRange,
            sampleCount = sampleCount,
            systemDesignCapacityMah = designCapacity,
        )
    }

    /**
     * Polls the battery directly rather than waiting for the service, so the
     * numbers on screen move while the user is looking at them.
     */
    @Composable
    private fun liveSnapshot(settings: BatterySettings): BatterySnapshot? {
        val snapshot by produceState<BatterySnapshot?>(
            initialValue = null,
            settings.currentUnitMode,
            settings.invertCurrentSign,
        ) {
            while (true) {
                value = reader.read(
                    broadcast = null,
                    unitMode = settings.currentUnitMode,
                    invertSign = settings.invertCurrentSign,
                )
                delay(LIVE_REFRESH_MS)
            }
        }
        return snapshot
    }

    @Composable
    private fun timeEstimates(
        snapshot: BatterySnapshot?,
        chargeTargetLevel: Int,
    ): TimeEstimates {
        val estimates by produceState(
            initialValue = EMPTY_ESTIMATES,
            snapshot?.level,
            snapshot?.charging,
            chargeTargetLevel,
        ) {
            val current = snapshot ?: return@produceState
            value = runCatching { repository.estimates(current, chargeTargetLevel) }
                .getOrDefault(EMPTY_ESTIMATES)
        }
        return estimates
    }

    @Composable
    private fun appDrain(since: Long): ImmutableList<AppDrain> {
        val drainFlow = remember(since) { repository.appDrainSinceFlow(since) }
        val rows by drainFlow.collectAsState(initial = emptyList())
        return remember(rows) {
            rows.map { row ->
                AppDrain(
                    packageName = row.packageName,
                    appLabel = appDrainEstimator.appLabel(row.packageName),
                    foregroundMs = row.foregroundMs,
                    estimatedMah = row.estimatedMah,
                    estimatedPercent = row.estimatedPercent,
                )
            }.toImmutableList()
        }
    }

    @Suppress("CyclomaticComplexMethod")
    override fun onEvent(event: BatteryUiEvent) {
        when (event) {
            is BatteryUiEvent.SetMonitoringEnabled -> edit {
                preferences.setMonitoringEnabled(event.enabled)
                controller.applySettings()
            }

            is BatteryUiEvent.SetDetailedNotification ->
                edit { preferences.setPersistentNotification(event.enabled) }

            is BatteryUiEvent.SetSamplingInterval ->
                edit { preferences.setSamplingIntervalSec(event.seconds) }

            is BatteryUiEvent.SetChargeAlarmEnabled ->
                edit { preferences.setChargeAlarmEnabled(event.enabled) }

            is BatteryUiEvent.SetChargeAlarmLevel ->
                edit { preferences.setChargeAlarmLevel(event.level) }

            is BatteryUiEvent.SetChargeAlarmSound ->
                edit { preferences.setChargeAlarmSound(event.enabled) }

            is BatteryUiEvent.SetChargeAlarmVibrate ->
                edit { preferences.setChargeAlarmVibrate(event.enabled) }

            is BatteryUiEvent.SetLowBatteryAlarmEnabled ->
                edit { preferences.setLowBatteryAlarmEnabled(event.enabled) }

            is BatteryUiEvent.SetLowBatteryLevel ->
                edit { preferences.setLowBatteryLevel(event.level) }

            is BatteryUiEvent.SetTemperatureAlarmEnabled ->
                edit { preferences.setTemperatureAlarmEnabled(event.enabled) }

            is BatteryUiEvent.SetTemperatureAlarmC ->
                edit { preferences.setTemperatureAlarmC(event.celsius) }

            is BatteryUiEvent.SetDesignCapacity ->
                edit { preferences.setDesignCapacityOverride(event.mah) }

            is BatteryUiEvent.SetTemperatureUnit ->
                edit { preferences.setTemperatureInFahrenheit(event.fahrenheit) }

            is BatteryUiEvent.SetCurrentUnitMode ->
                edit { preferences.setCurrentUnitMode(event.mode) }

            is BatteryUiEvent.SetInvertCurrentSign ->
                edit { preferences.setInvertCurrentSign(event.enabled) }

            is BatteryUiEvent.SetTrackAppDrain ->
                edit { preferences.setTrackAppDrain(event.enabled) }

            is BatteryUiEvent.SetRetentionDays ->
                edit { preferences.setRetentionDays(event.days) }

            is BatteryUiEvent.SetMinLevelDelta ->
                edit { preferences.setMinLevelDeltaForCapacity(event.delta) }

            is BatteryUiEvent.SelectHistoryRange -> historyRange = event.range

            BatteryUiEvent.RequestUsageAccess -> openUsageAccessSettings()
            BatteryUiEvent.RefreshAppDrain -> edit {
                repository.refreshOngoingAppDrain()
                usageAccessProbe++
            }

            BatteryUiEvent.ExportCsv -> exportCsv()
            BatteryUiEvent.ClearAllData -> edit { repository.clearAll() }
            BatteryUiEvent.OpenBatteryOptimizationSettings -> openBatteryOptimizationSettings()
        }
    }

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onFailure { Timber.w(it, "Battery: action failed") }
        }
    }

    private fun exportCsv() {
        viewModelScope.launch {
            val csv = runCatching { repository.exportCsv() }.getOrNull() ?: return@launch
            val intent = Intent(Intent.ACTION_SEND)
                .setType("text/csv")
                .putExtra(Intent.EXTRA_SUBJECT, "Ivy Wallet battery history")
                .putExtra(Intent.EXTRA_TEXT, csv)
            startExternal(Intent.createChooser(intent, "Export battery history"))
        }
    }

    private fun openUsageAccessSettings() {
        startExternal(appDrainEstimator.usageAccessSettingsIntent())
        usageAccessProbe++
    }

    private fun openBatteryOptimizationSettings() {
        startExternal(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
        )
    }

    private fun startExternal(intent: Intent) {
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Timber.w(it, "Battery: could not open $intent") }
    }

    companion object {
        private const val LIVE_REFRESH_MS = 2_000L
        private const val MAX_CHART_POINTS = 400
        private val EMPTY_ESTIMATES = TimeEstimates(null, null, null, null, null)
    }

    /**
     * A month of samples is far more than a chart can show; thinning them keeps
     * recomposition cheap without changing the shape of the curve.
     */
    private fun <T> List<T>.downsample(): List<T> {
        if (size <= MAX_CHART_POINTS) return this
        val step = size / MAX_CHART_POINTS
        return filterIndexed { index, _ -> index % step == 0 }
    }
}
