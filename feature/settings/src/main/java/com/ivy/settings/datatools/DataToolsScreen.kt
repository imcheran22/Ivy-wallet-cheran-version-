package com.ivy.settings.datatools

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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivy.domain.usecase.datatools.ArchivableAccount
import com.ivy.domain.usecase.datatools.BulkTransactionRow
import com.ivy.domain.usecase.datatools.DuplicateGroup
import com.ivy.legacy.utils.format
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R

@Composable
fun DataToolsScreenImpl(
    viewModel: DataToolsViewModel = screenScopedViewModel(),
) {
    val state by viewModel.state.collectAsState()
    DataToolsUi(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataToolsUi(
    state: DataToolsState,
    onEvent: (DataToolsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nav = navigation()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.data_tools), fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = state.tab.ordinal) {
                DataToolsTab.entries.forEach { tab ->
                    Tab(
                        selected = state.tab == tab,
                        onClick = { onEvent(DataToolsEvent.SelectTab(tab)) },
                        text = { Text(tabLabel(tab)) },
                    )
                }
            }

            state.message?.let { message ->
                MessageBar(message = message, onDismiss = { onEvent(DataToolsEvent.DismissMessage) })
            }

            when {
                state.loading -> Loading()
                state.tab == DataToolsTab.DUPLICATES -> DuplicatesTab(state, onEvent)
                state.tab == DataToolsTab.RECATEGORIZE -> RecategorizeTab(state, onEvent)
                state.tab == DataToolsTab.ACCOUNTS -> AccountsTab(state, onEvent)
                else -> BackupsTab(state, onEvent)
            }
        }
    }
}

@Composable
private fun Loading() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MessageBar(message: DataToolsMessage, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = when (message) {
                is DataToolsMessage.Merged ->
                    stringResource(R.string.duplicates_removed, message.removed)

                is DataToolsMessage.Recategorized ->
                    stringResource(R.string.transactions_recategorized, message.count)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
    }
}

@Composable
private fun DuplicatesTab(state: DataToolsState, onEvent: (DataToolsEvent) -> Unit) {
    if (state.duplicates.isEmpty()) {
        EmptyMessage(stringResource(R.string.no_duplicates_found))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.duplicates.size) { index ->
            DuplicateCard(group = state.duplicates[index], onEvent = onEvent)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun DuplicateCard(group: DuplicateGroup, onEvent: (DataToolsEvent) -> Unit) {
    val allIds = group.candidates.map { it.id }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.possible_duplicate, group.candidates.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            group.candidates.forEach { candidate ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = candidate.title
                                ?: candidate.categoryName
                                ?: stringResource(R.string.unspecified),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = listOfNotNull(
                                candidate.categoryName,
                                candidate.accountName,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Text(
                        text = "${candidate.amount.format(candidate.assetCode)} " +
                            candidate.assetCode,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Spacer(Modifier.padding(horizontal = 4.dp))

                    AssistChip(
                        onClick = {
                            onEvent(DataToolsEvent.Merge(keepId = candidate.id, allIds = allIds))
                        },
                        label = { Text(stringResource(R.string.keep_this_one)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecategorizeTab(state: DataToolsState, onEvent: (DataToolsEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.query,
                onValueChange = { onEvent(DataToolsEvent.SetQuery(it)) },
                label = { Text(stringResource(R.string.search)) },
                singleLine = true,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.only_uncategorized),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = state.onlyUncategorized,
                    onCheckedChange = { onEvent(DataToolsEvent.SetOnlyUncategorized(it)) },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onEvent(DataToolsEvent.SelectAll) }) {
                    Text(stringResource(R.string.select_all))
                }
                TextButton(onClick = { onEvent(DataToolsEvent.ClearSelection) }) {
                    Text(stringResource(R.string.clear))
                }
                Text(
                    text = stringResource(R.string.selected_count, state.selectedIds.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.selectedIds.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.move_selected_to),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.categories.forEach { category ->
                        FilterChip(
                            selected = false,
                            onClick = { onEvent(DataToolsEvent.ApplyCategory(category.id)) },
                            label = { Text(category.name, maxLines = 1) },
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        ) {
            items(state.rows.size) { index ->
                val row = state.rows[index]
                BulkRow(
                    row = row,
                    selected = row.id in state.selectedIds,
                    onToggle = { onEvent(DataToolsEvent.ToggleSelected(row.id)) },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun BulkRow(row: BulkTransactionRow, selected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title ?: stringResource(R.string.unspecified),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    row.categoryName ?: stringResource(R.string.unspecified),
                    row.accountName,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "${row.amount.format(row.assetCode)} ${row.assetCode}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AccountsTab(state: DataToolsState, onEvent: (DataToolsEvent) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
    ) {
        item {
            Text(
                modifier = Modifier.padding(vertical = 8.dp),
                text = stringResource(R.string.archive_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items(state.accounts.size) { index ->
            AccountRow(account = state.accounts[index], onEvent = onEvent)
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun AccountRow(account: ArchivableAccount, onEvent: (DataToolsEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = account.assetCode,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = account.archived,
            onCheckedChange = { onEvent(DataToolsEvent.SetArchived(account.id, it)) },
        )
    }
}

/**
 * Automatic backups, and the only two facts about them that matter: whether they are on, and
 * when the last one actually happened.
 */
@Composable
private fun BackupsTab(state: DataToolsState, onEvent: (DataToolsEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.automatic_backups),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.automatic_backups_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.autoBackupEnabled,
                onCheckedChange = { onEvent(DataToolsEvent.SetAutoBackup(it)) },
            )
        }

        Text(
            text = state.lastBackupEpochMs?.let {
                stringResource(R.string.last_backup, formatTimestamp(it))
            } ?: stringResource(R.string.no_backup_yet),
            style = MaterialTheme.typography.bodyMedium,
        )

        state.lastBackupResult?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedButton(onClick = { onEvent(DataToolsEvent.BackUpNow) }) {
            Text(stringResource(R.string.back_up_now))
        }
    }
}

private fun formatTimestamp(epochMs: Long): String =
    java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(epochMs))

@Composable
private fun EmptyMessage(text: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun tabLabel(tab: DataToolsTab): String = stringResource(
    when (tab) {
        DataToolsTab.DUPLICATES -> R.string.duplicates
        DataToolsTab.RECATEGORIZE -> R.string.recategorize
        DataToolsTab.ACCOUNTS -> R.string.accounts
        DataToolsTab.BACKUPS -> R.string.backups
    }
)
