package com.ivy.battery.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.battery.ui.tab.AppsTab
import com.ivy.battery.ui.tab.BatterySettingsTab
import com.ivy.battery.ui.tab.ChargingTab
import com.ivy.battery.ui.tab.DischargeTab
import com.ivy.battery.ui.tab.HealthTab
import com.ivy.battery.ui.tab.HistoryTab
import com.ivy.battery.ui.tab.OverviewTab
import com.ivy.battery.ui.theme.BatteryTheme
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import kotlinx.coroutines.launch

private enum class BatteryTab(val label: String) {
    OVERVIEW("Overview"),
    CHARGING("Charging"),
    DISCHARGE("Discharge"),
    HEALTH("Health"),
    HISTORY("History"),
    APPS("Apps"),
    SETTINGS("Settings"),
}

@Composable
fun BatteryScreenImpl(
    viewModel: BatteryViewModel = screenScopedViewModel(),
) {
    BatteryTheme {
        BatteryUi(
            state = viewModel.uiState(),
            onEvent = viewModel::onEvent,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun BatteryUi(
    state: BatteryUiState,
    onEvent: (BatteryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nav = navigation()
    val pagerState = rememberPagerState(pageCount = { BatteryTab.entries.size })
    val scope = rememberCoroutineScope()

    RequestNotificationPermission(enabled = state.settings.monitoringEnabled)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Battery",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            Column {
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = 12.dp,
                    containerColor = MaterialTheme.colorScheme.background,
                    divider = {},
                ) {
                    BatteryTab.entries.forEachIndexed { index, tab ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(tab.label) },
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (BatteryTab.entries[page]) {
                        BatteryTab.OVERVIEW -> OverviewTab(state = state)
                        BatteryTab.CHARGING -> ChargingTab(state = state, onEvent = onEvent)
                        BatteryTab.DISCHARGE -> DischargeTab(state = state, onEvent = onEvent)
                        BatteryTab.HEALTH -> HealthTab(state = state, onEvent = onEvent)
                        BatteryTab.HISTORY -> HistoryTab(state = state, onEvent = onEvent)
                        BatteryTab.APPS -> AppsTab(state = state, onEvent = onEvent)
                        BatteryTab.SETTINGS -> BatterySettingsTab(state = state, onEvent = onEvent)
                    }
                }
            }
        }
    }
}

/**
 * Background monitoring is useless without a notification to hang the
 * foreground service off, so ask for the permission as soon as the user opens
 * the screen with monitoring on.
 */
@Composable
private fun RequestNotificationPermission(enabled: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* the monitor degrades gracefully either way */ },
    )

    LaunchedEffect(enabled) {
        if (enabled) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
