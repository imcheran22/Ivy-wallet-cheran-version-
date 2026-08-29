package com.ivy.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivy.domain.usecase.insights.CategorySpend
import com.ivy.domain.usecase.insights.NetWorthPoint
import com.ivy.domain.usecase.insights.PayeeTotal
import com.ivy.legacy.utils.format
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Green
import com.ivy.wallet.ui.theme.Red
import com.ivy.wallet.ui.theme.components.charts.linechart.Function
import com.ivy.wallet.ui.theme.components.charts.linechart.IvyLineChart
import com.ivy.wallet.ui.theme.components.charts.linechart.Value
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun InsightsScreenImpl(
    viewModel: InsightsViewModel = screenScopedViewModel(),
) {
    val state by viewModel.state.collectAsState()
    InsightsUi(state = state)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InsightsUi(
    state: InsightsState,
    modifier: Modifier = Modifier,
) {
    val nav = navigation()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.insights),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        if (state.loading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { NetWorthCard(state = state) }
            item { ThisMonthCard(state = state) }

            if (state.categories.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.by_category)) }
                items(state.categories.size) { index ->
                    CategoryRow(spend = state.categories[index], currency = state.currency)
                }
            }

            if (state.topPayees.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.top_payees)) }
                items(state.topPayees.size) { index ->
                    PayeeRow(payee = state.topPayees[index], currency = state.currency)
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun NetWorthCard(state: InsightsState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.net_worth),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${state.netWorthNow.format(state.currency)} ${state.currency}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            DeltaText(
                delta = state.netWorthChange,
                currency = state.currency,
                positiveIsGood = true,
                labelRes = R.string.over_the_last_year,
            )

            if (state.netWorthPoints.size > 1) {
                Spacer(Modifier.height(12.dp))
                NetWorthChart(points = state.netWorthPoints, currency = state.currency)
            }
        }
    }
}

@Composable
private fun NetWorthChart(points: List<NetWorthPoint>, currency: String) {
    val values = points.mapIndexed { index, point ->
        Value(x = index.toDouble(), y = point.netWorth)
    }

    IvyLineChart(
        modifier = Modifier.fillMaxWidth(),
        height = 180.dp,
        functions = listOf(
            Function(values = values, color = Green, colorDown = Red)
        ),
        title = stringResource(R.string.net_worth),
        xLabel = { x ->
            points.getOrNull(x.roundToInt())?.date?.let { "${it.monthValue}/${it.year % 100}" }
                .orEmpty()
        },
        yLabel = { y -> "${y.format(currency)} $currency" },
    )
}

@Composable
private fun ThisMonthCard(state: InsightsState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.this_month_vs_last),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            AmountRow(
                label = stringResource(R.string.expenses),
                amount = state.currentSpent,
                currency = state.currency,
            )
            DeltaText(
                delta = state.spentDelta,
                currency = state.currency,
                positiveIsGood = false,
                labelRes = R.string.vs_last_month,
            )

            Spacer(Modifier.height(8.dp))

            AmountRow(
                label = stringResource(R.string.income),
                amount = state.currentIncome,
                currency = state.currency,
            )
            DeltaText(
                delta = state.currentIncome - state.previousIncome,
                currency = state.currency,
                positiveIsGood = true,
                labelRes = R.string.vs_last_month,
            )

            Spacer(Modifier.height(8.dp))

            AmountRow(
                label = stringResource(R.string.saved),
                amount = state.saved,
                currency = state.currency,
            )
        }
    }
}

@Composable
private fun AmountRow(label: String, amount: Double, currency: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "${amount.format(currency)} $currency",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * A change, coloured by whether it is good news.
 *
 * Spending more is red and earning more is green - the same arithmetic sign means opposite
 * things depending on which side of the ledger it lands on.
 */
@Composable
private fun DeltaText(
    delta: Double,
    currency: String,
    positiveIsGood: Boolean,
    labelRes: Int,
) {
    if (delta == 0.0) return

    val good = if (positiveIsGood) delta > 0 else delta < 0
    val sign = if (delta > 0) "+" else "-"

    Text(
        text = "$sign${abs(delta).format(currency)} $currency ${stringResource(labelRes)}",
        style = MaterialTheme.typography.bodySmall,
        color = if (good) Green else Red,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun CategoryRow(spend: CategorySpend, currency: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = spend.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (spend.color != 0) Color(spend.color) else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${spend.previous.format(currency)} $currency ${stringResource(R.string.last_month)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${spend.current.format(currency)} $currency",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = percentLabel(spend),
                style = MaterialTheme.typography.bodySmall,
                color = if (spend.delta > 0) Red else Green,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun percentLabel(spend: CategorySpend): String {
    val percent = spend.percentChange
    return when {
        percent != null -> {
            val sign = if (percent > 0) "+" else ""
            "$sign${percent.roundToInt()}%"
        }

        spend.previous == 0.0 -> stringResource(R.string.new_this_month)
        else -> ""
    }
}

@Composable
private fun PayeeRow(payee: PayeeTotal, currency: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = payee.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.times_this_month, payee.count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "${payee.amount.format(currency)} $currency",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        modifier = Modifier.padding(top = 8.dp),
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}
