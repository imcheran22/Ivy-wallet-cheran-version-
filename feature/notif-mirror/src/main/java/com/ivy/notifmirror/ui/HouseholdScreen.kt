package com.ivy.notifmirror.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivy.navigation.screenScopedViewModel
import kotlin.math.abs

/**
 * The household view: your spending and your partner's, in one place.
 *
 * Two people tracking money separately each see half the picture, and the half they can't see is
 * the half that explains the month. This screen exists to add the two halves up - and then to
 * answer the question that always follows it, which is who owes whom.
 */
@Composable
fun HouseholdScreenImpl(
    viewModel: HouseholdViewModel = screenScopedViewModel(),
) {
    val state by viewModel.state.collectAsState()

    MirrorScaffold(title = "Household") {
        if (state.loading) {
            Text(text = "Adding it up…", style = MaterialTheme.typography.bodyMedium)
            return@MirrorScaffold
        }

        Text(
            text = state.periodLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        CombinedCard(state = state)

        Spacer(Modifier.height(16.dp))

        SettleUpCard(state = state, onEvent = viewModel::onEvent)

        if (state.budgets.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionHeading("Budgets, both of you")
            state.budgets.forEach { budget ->
                Spacer(Modifier.height(12.dp))
                HouseholdBudgetRow(budget = budget, currency = state.currency)
            }
        }

        if (state.categories.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionHeading("Where it went")
            state.categories.forEach { category ->
                Spacer(Modifier.height(8.dp))
                CategoryRow(category = category, currency = state.currency)
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun CombinedCard(state: HouseholdState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Spent together",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = money(state.combinedSpend, state.currency),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "You", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = money(state.yourSpend, state.currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.partnerName,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = money(state.partnerSpend, state.currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettleUpCard(state: HouseholdState, onEvent: (HouseholdEvent) -> Unit) {
    val settlement = state.settlement ?: return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeading("Settle up")

            Spacer(Modifier.height(4.dp))

            Text(
                text = when {
                    settlement.settled -> "You are square for this period."
                    settlement.owedToYou > 0 ->
                        "${state.partnerName} owes you ${money(settlement.amount, state.currency)}"

                    else ->
                        "You owe ${state.partnerName} " +
                            money(settlement.amount, state.currency)
                },
                style = MaterialTheme.typography.bodyLarge,
            )

            Text(
                text = "Half the difference between what each of you paid - not half the total.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!settlement.settled) {
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = state.partnerName,
                    onValueChange = { onEvent(HouseholdEvent.SetPartnerName(it)) },
                    label = { Text("Their name") },
                    singleLine = true,
                )

                Spacer(Modifier.height(8.dp))

                if (state.settlementRecorded) {
                    Text(
                        text = "Recorded as a loan. It stays there until it's paid.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Button(onClick = { onEvent(HouseholdEvent.RecordSettlement) }) {
                        Text("Record as a loan")
                    }
                }
            }
        }
    }
}

@Composable
private fun HouseholdBudgetRow(budget: HouseholdBudget, currency: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                modifier = Modifier.weight(1f),
                text = budget.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${money(budget.combined, currency)} / ${money(budget.amount, currency)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (budget.overspent) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }

        Spacer(Modifier.height(4.dp))

        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            progress = {
                if (budget.amount > 0) {
                    (budget.combined / budget.amount).toFloat().coerceIn(0f, 1f)
                } else {
                    0f
                }
            },
        )

        Text(
            text = "You ${money(budget.yours, currency)} · " +
                "them ${money(budget.partners, currency)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CategoryRow(category: HouseholdCategory, currency: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "You ${money(category.yours, currency)} · " +
                    "them ${money(category.partners, currency)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = money(category.combined, currency),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun money(amount: Double, currency: String): String =
    "%,.2f %s".format(abs(amount), currency)
