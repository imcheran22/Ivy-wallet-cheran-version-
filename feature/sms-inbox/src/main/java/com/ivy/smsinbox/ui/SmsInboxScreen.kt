package com.ivy.smsinbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivy.data.model.CategoryId
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import kotlinx.collections.immutable.ImmutableList
import java.util.UUID

@Composable
fun SmsInboxScreenImpl(
    viewModel: SmsInboxViewModel = screenScopedViewModel(),
) {
    val state by viewModel.state.collectAsState()
    SmsInboxUi(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmsInboxUi(
    state: SmsInboxUiState,
    onEvent: (SmsInboxEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nav = navigation()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = "Sort inbox", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            UnsortedSummary(state = state)
            Spacer(Modifier.height(12.dp))

            when {
                state.loading -> CenteredMessage { CircularProgressIndicator() }

                state.categories.isEmpty() -> CenteredMessage {
                    EmptyMessage(
                        title = "No categories yet",
                        body = "Create a few categories first - sorting is picking one, " +
                            "so there has to be something to pick.",
                    )
                }

                state.cards.isEmpty() -> CenteredMessage {
                    EmptyMessage(
                        title = "Nothing left to sort",
                        body = "Every auto-imported transaction has a category. " +
                            "New ones land here as your bank texts you.",
                        action = if (state.skippedCount > 0) {
                            {
                                TextButton(
                                    onClick = { onEvent(SmsInboxEvent.RevisitSkipped) },
                                ) { Text("Revisit skipped") }
                            }
                        } else {
                            null
                        },
                    )
                }

                else -> SortingCard(
                    card = state.cards.first(),
                    state = state,
                    onEvent = onEvent,
                )
            }
        }
    }
}

/**
 * Uncategorised spending gets its own line rather than being quietly left out of the totals.
 */
@Composable
private fun UnsortedSummary(state: SmsInboxUiState) {
    if (state.loading) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "${state.cards.size} left to sort",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Unsorted: ${state.unsortedExpenseLabel} out" +
                    if (state.unsortedIncomeLabel.isNotBlank()) {
                        ", ${state.unsortedIncomeLabel} in"
                    } else {
                        ""
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.sortedThisSession > 0) {
            Text(
                text = "${state.sortedThisSession} sorted",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * A fixed-height card: header and actions are pinned, only the middle scrolls. Letting the card
 * grow with its content is how the Save button ends up below the fold on a phone, which makes
 * the primary action require a scroll to reach.
 */
@Suppress("LongMethod")
@Composable
private fun SortingCard(
    card: SmsInboxCard,
    state: SmsInboxUiState,
    onEvent: (SmsInboxEvent) -> Unit,
) {
    var selected by rememberSaveable(card.transactionId.value) {
        mutableStateOf(card.suggestedCategoryId?.value)
    }
    var rememberPayee by rememberSaveable(card.transactionId.value) { mutableStateOf(true) }

    // A new card can arrive with a suggestion while the old selection is still held.
    LaunchedEffect(card.transactionId.value) {
        selected = card.suggestedCategoryId?.value
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(CARD_HEIGHT),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            CardHeader(card = card)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(12.dp))
                card.originalSms?.let { original ->
                    OriginalMessage(text = original)
                    Spacer(Modifier.height(12.dp))
                }
                if (card.suggestionReason != null && selected == card.suggestedCategoryId?.value) {
                    SuggestionReason(reason = card.suggestionReason)
                    Spacer(Modifier.height(8.dp))
                }
                CategoryPicker(
                    categories = state.categories,
                    selectedId = selected,
                    onSelect = { selected = it },
                )
                if (state.missingDefaultCategories) {
                    TextButton(onClick = { onEvent(SmsInboxEvent.CreateDefaultCategories) }) {
                        Text("Add the sorting categories")
                    }
                }
            }

            if (card.payee != null) {
                RememberPayeeToggle(
                    payee = card.payee,
                    checked = rememberPayee,
                    onCheckedChange = { rememberPayee = it },
                )
            }
            Spacer(Modifier.height(8.dp))
            CardActions(
                canSave = selected != null,
                onSkip = { onEvent(SmsInboxEvent.Skip(card.transactionId)) },
                onSave = {
                    selected?.let {
                        onEvent(
                            SmsInboxEvent.Save(
                                transactionId = card.transactionId,
                                categoryId = CategoryId(it),
                                rememberPayee = rememberPayee && card.payee != null,
                            )
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun CardHeader(card: SmsInboxCard) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            // weight() is what bounds this column's width. Without it a long payee measures
            // at its full intrinsic width, the maxLines/ellipsis below never kick in, and the
            // amount gets pushed off the right edge of the screen.
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Text(
                text = card.payee ?: "Payment with no name",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = card.whenLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (card.timesInQueue > 1 && card.payee != null) {
                Text(
                    text = "${card.timesInQueue} more to ${card.payee} waiting",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = card.amountLabel,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            // Everything in this queue is spending, so colouring it red would carry no
            // information at all - only money coming in is worth distinguishing.
            color = if (card.isIncome) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/**
 * The bank's original text.
 *
 * A payee the parser could not name leaves nothing on the card to recognise - an amount and a
 * timestamp are not a memory. The message almost always is: it carries the merchant's own
 * spelling, the card used, or a reference that can be searched. Showing it is the difference
 * between sorting the queue and guessing at it.
 */
@Composable
private fun OriginalMessage(text: String) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { expanded = !expanded }
            .padding(12.dp),
    ) {
        Text(
            text = "The bank's message",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_SMS_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val COLLAPSED_SMS_LINES = 3

@Composable
private fun SuggestionReason(reason: String) {
    Text(
        text = "Suggested because $reason",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPicker(
    categories: ImmutableList<CategoryOption>,
    selectedId: UUID?,
    onSelect: (UUID) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        categories.chunked(CATEGORIES_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { category ->
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = category.id.value == selectedId,
                        onClick = { onSelect(category.id.value) },
                        colors = FilterChipDefaults.filterChipColors(),
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(category.color), CircleShape)
                            )
                        },
                        label = {
                            Text(
                                text = category.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
                repeat(CATEGORIES_PER_ROW - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RememberPayeeToggle(
    payee: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = "Remember $payee",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CardActions(
    canSave: Boolean,
    onSkip: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(modifier = Modifier.weight(1f), onClick = onSkip) {
            Text("Later")
        }
        Button(modifier = Modifier.weight(1f), enabled = canSave, onClick = onSave) {
            Text("Save")
        }
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        content()
    }
}

@Composable
private fun EmptyMessage(
    title: String,
    body: String,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(WIDTH_EMPTY_MESSAGE),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action?.invoke()
    }
}

private val CARD_HEIGHT = 420.dp
private val WIDTH_EMPTY_MESSAGE = 280.dp
private const val CATEGORIES_PER_ROW = 2
