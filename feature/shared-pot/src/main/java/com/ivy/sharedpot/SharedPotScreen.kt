package com.ivy.sharedpot

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.legacy.ui.theme.components.BudgetBattery
import com.ivy.legacy.utils.format
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.wallet.ui.theme.Gray
import com.ivy.wallet.ui.theme.Green
import com.ivy.wallet.ui.theme.Orange
import com.ivy.wallet.ui.theme.Red
import com.ivy.wallet.ui.theme.components.BackButton
import com.ivy.wallet.ui.theme.modal.edit.AmountModal
import java.util.UUID

@Composable
fun BoxWithConstraintsScope.SharedPotScreenImpl(
    viewModel: SharedPotViewModel = screenScopedViewModel(),
) {
    val state = viewModel.uiState()
    UI(state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun BoxWithConstraintsScope.UI(
    state: SharedPotState,
    onEvent: (SharedPotEvent) -> Unit,
) {
    val nav = navigation()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(20.dp))

            BackButton { nav.back() }

            Spacer(Modifier.width(16.dp))

            Text(
                text = "Shared pot",
                style = UI.typo.h2.style(fontWeight = FontWeight.ExtraBold)
            )
        }

        Spacer(Modifier.height(24.dp))

        if (state.setUp) {
            PotSummary(state = state, onEvent = onEvent)
        } else if (!state.loading) {
            Setup(state = state, onEvent = onEvent)
        }

        Spacer(Modifier.height(120.dp))
    }

    if (state.editingLimit) {
        LimitModal(state = state, onEvent = onEvent)
    }
}

// ------------------------------------------------------------------------------------------
// Set up
// ------------------------------------------------------------------------------------------

/**
 * Two questions, asked once: which account is the shared one, and what are you keeping it
 * under. Nothing is created - naming an account you already have is what makes it shared,
 * which is also what lets the other phone see the same pot through the cloud sync.
 */
@Composable
private fun Setup(
    state: SharedPotState,
    onEvent: (SharedPotEvent) -> Unit,
) {
    SectionCaption("Choose the account you share")

    Spacer(Modifier.height(12.dp))

    for (option in state.accountOptions) {
        AccountRow(
            option = option,
            selected = false,
            onClick = { onEvent(SharedPotEvent.PickAccount(option.id)) }
        )
        Spacer(Modifier.height(8.dp))
    }

    if (state.accountOptions.isEmpty()) {
        Text(
            modifier = Modifier.padding(horizontal = 24.dp),
            text = "Create an account first - the shared pot is one of your accounts, " +
                "not a separate thing to keep in step with.",
            style = UI.typo.b2.style(color = Gray)
        )
    }

    if (state.potName.isNotBlank()) {
        Spacer(Modifier.height(24.dp))

        SectionCaption("Monthly limit")

        Spacer(Modifier.height(12.dp))

        BigActionRow(
            label = "Set a limit for ${state.potName}",
            value = state.limit.takeIf { it > 0.0 }
                ?.let { "${it.format(state.currency)} ${state.currency}" }
                ?: "Not set",
            onClick = { onEvent(SharedPotEvent.EditLimit) }
        )
    }
}

// ------------------------------------------------------------------------------------------
// The pot
// ------------------------------------------------------------------------------------------

@Composable
private fun PotSummary(
    state: SharedPotState,
    onEvent: (SharedPotEvent) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(
            text = state.potName,
            style = UI.typo.b1.style(fontWeight = FontWeight.ExtraBold)
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = if (state.overspent) {
                "Over by ${(-state.left).format(state.currency)} ${state.currency}"
            } else {
                "${state.left.format(state.currency)} ${state.currency} left of " +
                    "${state.limit.format(state.currency)}"
            },
            style = UI.typo.nH2.style(
                fontWeight = FontWeight.ExtraBold,
                color = if (state.overspent) Red else UI.colors.pureInverse
            ),
            modifier = Modifier.testTag("shared_pot_left")
        )
    }

    Spacer(Modifier.height(16.dp))

    BudgetBattery(
        modifier = Modifier.padding(horizontal = 16.dp),
        currency = state.currency,
        expenses = state.spent,
        budget = state.limit,
        backgroundNotFilled = UI.colors.medium,
    )

    Spacer(Modifier.height(24.dp))

    DailyAllowance(state = state)

    Spacer(Modifier.height(16.dp))

    Pace(state = state)

    if (state.added > 0.0) {
        Spacer(Modifier.height(16.dp))

        StatRow(
            label = "Added to the pot this month",
            value = "${state.added.format(state.currency)} ${state.currency}"
        )
    }

    Spacer(Modifier.height(28.dp))

    SectionCaption("This month")

    Spacer(Modifier.height(12.dp))

    if (state.recent.isEmpty()) {
        Text(
            modifier = Modifier.padding(horizontal = 24.dp),
            text = "Nothing spent from the pot yet this month.",
            style = UI.typo.b2.style(color = Gray)
        )
    } else {
        for (entry in state.recent) {
            EntryRow(entry = entry, currency = state.currency)
            Spacer(Modifier.height(4.dp))
        }
    }

    Spacer(Modifier.height(28.dp))

    BigActionRow(
        label = "Monthly limit",
        value = "${state.limit.format(state.currency)} ${state.currency}",
        onClick = { onEvent(SharedPotEvent.EditLimit) }
    )

    Spacer(Modifier.height(8.dp))

    BigActionRow(
        label = "Shared account",
        value = state.potName,
        onClick = { onEvent(SharedPotEvent.OpenAccountPicker) }
    )

    if (state.pickingAccount) {
        Spacer(Modifier.height(12.dp))

        for (option in state.accountOptions) {
            AccountRow(
                option = option,
                selected = option.name == state.potName,
                onClick = { onEvent(SharedPotEvent.PickAccount(option.id)) }
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    Spacer(Modifier.height(20.dp))

    Text(
        modifier = Modifier.padding(horizontal = 24.dp),
        text = "The limit is kept on this phone. Point both phones at the same cloud sync " +
            "project and the spending itself stays in step.",
        style = UI.typo.c.style(color = Gray)
    )
}

/**
 * The number the screen exists for. A month total says how it went; this says what today can
 * afford, which is the question being asked while standing at a counter.
 */
@Composable
private fun DailyAllowance(state: SharedPotState) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(UI.shapes.r4)
            .background(UI.colors.medium)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = "Safe to spend today",
            style = UI.typo.c.style(color = Gray, fontWeight = FontWeight.Bold)
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "${state.safeDailySpend.format(state.currency)} ${state.currency}",
            style = UI.typo.h2.style(
                fontWeight = FontWeight.ExtraBold,
                color = if (state.overspent) Red else Green
            ),
            modifier = Modifier.testTag("shared_pot_daily")
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = if (state.overspent) {
                "The limit is already spent, with ${state.daysLeft} " +
                    "${dayWord(state.daysLeft)} of the month left."
            } else {
                "What is left, spread over the ${state.daysLeft} " +
                    "${dayWord(state.daysLeft)} still to come."
            },
            style = UI.typo.c.style(color = Gray)
        )
    }
}

@Composable
private fun Pace(state: SharedPotState) {
    val ahead = state.paceDelta > 0
    val magnitude = kotlin.math.abs(state.paceDelta)

    StatRow(
        label = if (ahead) "Ahead of an even pace by" else "Behind an even pace by",
        value = "${magnitude.format(state.currency)} ${state.currency}",
        valueColor = if (ahead) Orange else Green,
    )
}

// ------------------------------------------------------------------------------------------
// Pieces
// ------------------------------------------------------------------------------------------

@Composable
private fun SectionCaption(text: String) {
    Text(
        modifier = Modifier.padding(horizontal = 24.dp),
        text = text.uppercase(),
        style = UI.typo.c.style(color = Gray, fontWeight = FontWeight.Black)
    )
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = UI.colors.pureInverse,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = UI.typo.b2.style(color = Gray)
        )
        Text(
            text = value,
            style = UI.typo.b2.style(color = valueColor, fontWeight = FontWeight.ExtraBold)
        )
    }
}

@Composable
private fun BigActionRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(UI.shapes.r4)
            .background(UI.colors.medium)
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = UI.typo.b2.style(fontWeight = FontWeight.Bold)
        )
        Text(
            text = value,
            style = UI.typo.b2.style(color = Gray, fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
private fun AccountRow(
    option: AccountOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(UI.shapes.r4)
            .background(if (selected) UI.colors.pure else UI.colors.medium)
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = option.name,
            style = UI.typo.b2.style(fontWeight = FontWeight.Bold)
        )
        Text(
            text = option.currency,
            style = UI.typo.c.style(color = Gray, fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun EntryRow(entry: PotEntry, currency: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = UI.typo.b2.style(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = entry.timeLabel,
                style = UI.typo.c.style(color = Gray)
            )
        }

        Text(
            text = buildString {
                append(if (entry.income) "+" else "-")
                append(entry.amount.format(currency))
                append(" ")
                append(currency)
            },
            style = UI.typo.b2.style(
                fontWeight = FontWeight.ExtraBold,
                color = if (entry.income) Green else UI.colors.pureInverse
            )
        )
    }
}

@Composable
private fun BoxWithConstraintsScope.LimitModal(
    state: SharedPotState,
    onEvent: (SharedPotEvent) -> Unit,
) {
    val id = remember(state.limit) { UUID.randomUUID() }
    AmountModal(
        id = id,
        visible = true,
        currency = state.currency,
        initialAmount = state.limit.takeIf { it > 0.0 },
        dismiss = { onEvent(SharedPotEvent.DismissLimit) },
        onAmountChanged = { onEvent(SharedPotEvent.SetLimit(it)) },
    )
}

private fun dayWord(days: Int): String = if (days == 1) "day" else "days"
