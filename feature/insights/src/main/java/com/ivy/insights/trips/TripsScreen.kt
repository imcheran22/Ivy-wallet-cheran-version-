package com.ivy.insights.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivy.domain.usecase.trip.TripSummary
import com.ivy.legacy.utils.format
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun TripsScreenImpl(
    viewModel: TripsViewModel = screenScopedViewModel(),
) {
    val state by viewModel.state.collectAsState()
    TripsUi(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripsUi(
    state: TripsState,
    onEvent: (TripsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nav = navigation()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.trips), fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEvent(TripsEvent.AddTrip) }) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_trip))
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.trips.isEmpty() && !state.loading) {
                item {
                    Text(
                        modifier = Modifier.padding(vertical = 24.dp),
                        text = stringResource(R.string.no_trips_yet),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            items(state.trips.size) { index ->
                val summary = state.trips[index]
                TripCard(
                    summary = summary,
                    expanded = state.expandedTripId == summary.trip.id,
                    onEvent = onEvent,
                )
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    state.draft?.let { draft ->
        TripDialog(state = state, draft = draft, onEvent = onEvent)
    }
}

@Composable
private fun TripCard(
    summary: TripSummary,
    expanded: Boolean,
    onEvent: (TripsEvent) -> Unit,
) {
    val trip = summary.trip

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEvent(TripsEvent.ToggleExpanded(trip.id)) },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trip.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${trip.startDate} - ${trip.endDateInclusive}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onEvent(TripsEvent.EditTrip(trip.id)) }) {
                    Icon(Icons.Rounded.Edit, contentDescription = null)
                }
                IconButton(onClick = { onEvent(TripsEvent.DeleteTrip(trip.id)) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = null)
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "${summary.spent.format(summary.baseCurrency)} ${summary.baseCurrency}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            summary.spentInTripCurrency?.let { inTripCurrency ->
                Text(
                    text = "${inTripCurrency.format(trip.currency.orEmpty())} ${trip.currency}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = stringResource(
                    R.string.trip_per_day,
                    summary.perDay.format(summary.baseCurrency),
                    summary.baseCurrency,
                    trip.days,
                    summary.transactionCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                summary.categories.forEach { category ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = category.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${category.amount.format(summary.baseCurrency)} " +
                                summary.baseCurrency,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripDialog(
    state: TripsState,
    draft: TripDraft,
    onEvent: (TripsEvent) -> Unit,
) {
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { onEvent(TripsEvent.DismissDraft) },
        title = {
            Text(stringResource(if (draft.id == null) R.string.add_trip else R.string.edit_trip))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { onEvent(TripsEvent.UpdateDraft(draft.copy(name = it))) },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { pickingStart = true }) {
                        Text("${stringResource(R.string.from)}: ${draft.startDate}")
                    }
                    TextButton(onClick = { pickingEnd = true }) {
                        Text("${stringResource(R.string.to)}: ${draft.endDate}")
                    }
                }

                OutlinedTextField(
                    value = draft.currency,
                    onValueChange = { onEvent(TripsEvent.UpdateDraft(draft.copy(currency = it))) },
                    label = { Text(stringResource(R.string.trip_currency)) },
                    singleLine = true,
                )

                if (state.accounts.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.accounts),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.accounts.forEach { account ->
                            val selected = account.id in draft.accountIds
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val updated = if (selected) {
                                        draft.accountIds - account.id
                                    } else {
                                        draft.accountIds + account.id
                                    }
                                    onEvent(
                                        TripsEvent.UpdateDraft(draft.copy(accountIds = updated))
                                    )
                                },
                                label = { Text(account.name, maxLines = 1) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = draft.isValid,
                onClick = { onEvent(TripsEvent.SaveDraft) },
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(TripsEvent.DismissDraft) }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )

    if (pickingStart) {
        DatePickerSheet(
            initial = draft.startDate,
            onDismiss = { pickingStart = false },
            onPicked = {
                onEvent(TripsEvent.UpdateDraft(draft.copy(startDate = it)))
                pickingStart = false
            },
        )
    }

    if (pickingEnd) {
        DatePickerSheet(
            initial = draft.endDate,
            onDismiss = { pickingEnd = false },
            onPicked = {
                onEvent(TripsEvent.UpdateDraft(draft.copy(endDate = it)))
                pickingEnd = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPicked: (LocalDate) -> Unit,
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onPicked(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        )
                    }
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    ) {
        DatePicker(state = pickerState)
    }
}
