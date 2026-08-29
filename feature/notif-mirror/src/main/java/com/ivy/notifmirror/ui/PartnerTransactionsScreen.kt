package com.ivy.notifmirror.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivy.navigation.screenScopedViewModel
import com.ivy.notifmirror.sync.PartnerTransaction
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale

/**
 * Where your partner's money went - not just how much of it there was.
 *
 * Three totals on a card is a scoreboard, and a scoreboard is not something you can act on.
 * What makes shared spending legible is the shape of it: which categories dominate, what the
 * single biggest hit was, and what happened on which day. The totals stay, but they are the
 * header, not the screen.
 */
@Composable
fun PartnerTransactionsScreenImpl(
    viewModel: PartnerTransactionsViewModel = screenScopedViewModel(),
) {
    val state by viewModel.state.collectAsState()

    MirrorScaffold(
        title = "Partner's spending",
        actions = {
            if (state.transactions.isNotEmpty()) {
                TextButton(onClick = viewModel::clearAll) { Text("Clear") }
            }
        },
    ) {
        if (state.transactions.isEmpty()) {
            EmptyState()
            return@MirrorScaffold
        }

        SummaryHeader(
            income = state.totalIncome,
            expense = state.totalExpense,
            currency = state.mainCurrency,
        )

        state.biggestExpense?.let {
            Spacer(Modifier.height(12.dp))
            BiggestExpenseCard(it)
        }

        if (state.categories.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionTitle("Where it went")
            Spacer(Modifier.height(12.dp))
            state.categories.forEach { slice ->
                CategoryBar(slice = slice, currency = state.mainCurrency)
                Spacer(Modifier.height(12.dp))
            }
        }

        if (state.uncategorisedCount > 0) {
            Text(
                text = "${state.uncategorisedCount} payments arrived without a category, so " +
                    "they are grouped together above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionTitle("Activity")
        Spacer(Modifier.height(4.dp))

        state.days.forEach { day ->
            Spacer(Modifier.height(12.dp))
            DayHeader(label = day.label, total = day.total, currency = state.mainCurrency)
            day.transactions.forEach { tx ->
                TransactionRow(
                    tx = tx,
                    accepted = tx.key in state.acceptedKeys,
                    onAccept = { viewModel.accept(tx) },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun EmptyState() {
    Spacer(Modifier.height(64.dp))
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Nothing shared yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Once Couple Mirror is paired on both phones, your partner's transactions " +
                "appear here as they happen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SummaryHeader(income: Double, expense: Double, currency: String) {
    val net = income - expense
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = if (net >= 0) "Up ${formatAmount(net, currency)}" else
                    "Down ${formatAmount(-net, currency)}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (net >= 0) positive() else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Totals(
                    modifier = Modifier.weight(1f),
                    label = "Received",
                    value = formatAmount(income, currency),
                    tint = positive(),
                )
                Totals(
                    modifier = Modifier.weight(1f),
                    label = "Spent",
                    value = formatAmount(expense, currency),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun Totals(modifier: Modifier, label: String, value: String, tint: Color) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = tint,
        )
    }
}

@Composable
private fun BiggestExpenseCard(tx: PartnerTransaction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Biggest single spend",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = tx.title.ifBlank { "Unnamed" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = formatAmount(tx.amount, tx.currency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * A bar plus the number, never a bar alone: the bar makes the ranking obvious at a glance and
 * the number is what you actually quote to each other.
 */
@Composable
private fun CategoryBar(slice: CategorySlice, currency: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                modifier = Modifier.weight(1f),
                text = slice.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = formatAmount(slice.amount, currency),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(slice.share.coerceIn(MIN_BAR_SHARE, 1f))
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${(slice.share * PERCENT).toInt()}% · ${slice.count} payments",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayHeader(label: String, total: Double, currency: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(
            text = if (total >= 0) {
                "+${formatAmount(total, currency)}"
            } else {
                formatAmount(total, currency)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (total >= 0) positive() else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * One mirrored transaction, with the one action that turns it from news into bookkeeping.
 */
@Composable
private fun TransactionRow(
    tx: PartnerTransaction,
    accepted: Boolean,
    onAccept: () -> Unit,
) {
    val isIncome = tx.type == "INCOME"
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (isIncome) positive() else MaterialTheme.colorScheme.onSurfaceVariant
                ),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tx.title.ifBlank { "Unnamed" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    tx.category.takeIf { it.isNotBlank() },
                    tx.accountName.takeIf { it.isNotBlank() },
                    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(tx.dateTime)),
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (isIncome) {
                "+${formatAmount(tx.amount, tx.currency)}"
            } else {
                formatAmount(tx.amount, tx.currency)
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (isIncome) positive() else MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.width(4.dp))

        if (accepted) {
            Text(
                text = "In your books",
                style = MaterialTheme.typography.labelSmall,
                color = positive(),
                fontWeight = FontWeight.SemiBold,
            )
        } else {
            TextButton(onClick = onAccept) { Text("Add to mine") }
        }
    }
}

/**
 * Spending is left in the ordinary text colour. Painting every expense red states an opinion
 * the app has not earned - most of these are groceries and fuel, not mistakes - and once
 * everything is red, the colour stops carrying information at all.
 */
@Composable
private fun positive(): Color = MaterialTheme.colorScheme.secondary

private fun formatAmount(amount: Double, currencyCode: String): String = try {
    NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
        currency = Currency.getInstance(currencyCode)
    }.format(amount)
} catch (_: IllegalArgumentException) {
    "%s %.2f".format(currencyCode.ifBlank { "" }, amount).trim()
}

private const val PERCENT = 100
private const val MIN_BAR_SHARE = 0.02f
