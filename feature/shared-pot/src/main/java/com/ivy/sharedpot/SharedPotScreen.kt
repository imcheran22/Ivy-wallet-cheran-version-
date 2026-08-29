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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.base.model.TransactionType
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.legacy.ui.theme.components.BudgetBattery
import com.ivy.legacy.utils.format
import com.ivy.navigation.EditTransactionScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.wallet.ui.theme.Gray
import com.ivy.wallet.ui.theme.Green
import com.ivy.wallet.ui.theme.Orange
import com.ivy.wallet.ui.theme.Red
import com.ivy.wallet.ui.theme.components.BackButton
import com.ivy.wallet.ui.theme.modal.DeleteModal
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
            // Every screen paints its own ground in this app. Without it the window shows
            // through black and every line that isn't inside a card becomes invisible.
            .background(UI.colors.pure)
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
                style = UI.typo.h2.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.ExtraBold,
                )
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

    DeleteModal(
        visible = state.confirmingRemove,
        title = "Stop sharing this account?",
        description = "The account and every transaction in it stay exactly as they are. " +
            "Only the monthly limit and the pairing are forgotten.",
        dismiss = { onEvent(SharedPotEvent.DismissRemove) },
        // "Delete" would misdescribe it: this forgets a pairing, it does not remove money.
        buttonText = "Stop sharing",
        onDelete = { onEvent(SharedPotEvent.RemovePot) },
    )
}

// ------------------------------------------------------------------------------------------
// Set up
// ------------------------------------------------------------------------------------------

@Composable
private fun Setup(
    state: SharedPotState,
    onEvent: (SharedPotEvent) -> Unit,
) {
    Caption("Which account do you share?")

    Spacer(Modifier.height(4.dp))

    Explain(
        "Pick an account you both spend from. Nothing new is created and nothing moves - " +
            "the pot is that account, with a limit on top."
    )

    Spacer(Modifier.height(16.dp))

    for (option in state.accountOptions) {
        AccountRow(
            option = option,
            selected = option.name == state.potName,
            onClick = { onEvent(SharedPotEvent.PickAccount(option.id)) }
        )
        Spacer(Modifier.height(8.dp))
    }

    if (state.accountOptions.isEmpty()) {
        Explain("Create an account first - the pot has to be one of your accounts.")
    }

    if (state.potName.isNotBlank()) {
        Spacer(Modifier.height(28.dp))

        Caption("What are you keeping it under?")

        Spacer(Modifier.height(12.dp))

        RowCard(
            label = "Monthly limit",
            value = state.limit.takeIf { it > 0.0 }
                ?.let { "${it.format(state.currency)} ${state.currency}" }
                ?: "Tap to set",
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
    val nav = navigation()

    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(
            text = state.potName,
            style = UI.typo.c.style(color = Gray, fontWeight = FontWeight.Bold)
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "${state.left.coerceAtLeast(0.0).format(state.currency)} ${state.currency}",
            style = UI.typo.h1.style(
                fontWeight = FontWeight.ExtraBold,
                color = if (state.overspent) Red else UI.colors.pureInverse,
            ),
            modifier = Modifier.testTag("shared_pot_left")
        )

        Spacer(Modifier.height(2.dp))

        Text(
            text = if (state.overspent) {
                "Over the ${state.limit.format(state.currency)} limit by " +
                    "${(-state.left).format(state.currency)}"
            } else {
                "left of ${state.limit.format(state.currency)} this month · " +
                    "${state.spent.format(state.currency)} spent"
            },
            style = UI.typo.b2.style(color = Gray)
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

    Spacer(Modifier.height(12.dp))

    PaceLine(state = state)

    if (state.added > 0.0) {
        Spacer(Modifier.height(6.dp))
        Explain("${state.added.format(state.currency)} ${state.currency} was paid into this account this month.")
    }

    Spacer(Modifier.height(28.dp))

    Caption("Spending this month")

    Spacer(Modifier.height(8.dp))

    if (state.recent.isEmpty()) {
        Explain("Nothing has been spent from the pot yet this month.")
    } else {
        for (entry in state.recent) {
            EntryRow(
                entry = entry,
                currency = state.currency,
                onClick = {
                    nav.navigateTo(
                        EditTransactionScreen(
                            initialTransactionId = entry.id,
                            type = TransactionType.EXPENSE,
                        )
                    )
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        Explain("Tap a payment to edit or delete it.")
    }

    Spacer(Modifier.height(28.dp))

    RowCard(
        label = "Monthly limit",
        value = "${state.limit.format(state.currency)} ${state.currency}",
        onClick = { onEvent(SharedPotEvent.EditLimit) }
    )

    Spacer(Modifier.height(8.dp))

    RowCard(
        label = "Shared account",
        value = state.potName,
        onClick = { onEvent(SharedPotEvent.OpenAccountPicker) }
    )

    if (state.pickingAccount) {
        Spacer(Modifier.height(8.dp))

        for (option in state.accountOptions) {
            AccountRow(
                option = option,
                selected = option.name == state.potName,
                onClick = { onEvent(SharedPotEvent.PickAccount(option.id)) }
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    Spacer(Modifier.height(8.dp))

    RowCard(
        label = "Stop sharing",
        value = "Remove",
        valueColor = Red,
        onClick = { onEvent(SharedPotEvent.ConfirmRemove) }
    )

    Spacer(Modifier.height(20.dp))

    Explain(
        "The limit is kept on this phone. Point both phones at the same cloud sync project " +
            "and the spending itself stays in step."
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
                color = if (state.overspent) Red else Green,
            ),
            modifier = Modifier.testTag("shared_pot_daily")
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = if (state.overspent) {
                "The limit is already spent, with ${state.daysLeft} " +
                    "${dayWord(state.daysLeft)} of the month to go."
            } else {
                "That is what is left, split evenly across the ${state.daysLeft} " +
                    "${dayWord(state.daysLeft)} to the end of the month."
            },
            style = UI.typo.c.style(color = Gray)
        )
    }
}

/**
 * Pace in a sentence rather than a signed number. "Behind an even pace by 5.55" made the
 * reader do the interpreting; whether that is good news is the part worth stating.
 */
@Composable
private fun PaceLine(state: SharedPotState) {
    val ahead = state.paceDelta > 0
    val magnitude = kotlin.math.abs(state.paceDelta)
    if (magnitude < 1.0) return

    Text(
        modifier = Modifier.padding(horizontal = 24.dp),
        text = if (ahead) {
            "Spending faster than the month allows - " +
                "${magnitude.format(state.currency)} ${state.currency} ahead by today."
        } else {
            "Comfortably inside the limit - " +
                "${magnitude.format(state.currency)} ${state.currency} under by today."
        },
        style = UI.typo.b2.style(color = if (ahead) Orange else Green)
    )
}

// ------------------------------------------------------------------------------------------
// Pieces
// ------------------------------------------------------------------------------------------

@Composable
private fun Caption(text: String) {
    Text(
        modifier = Modifier.padding(horizontal = 24.dp),
        text = text,
        style = UI.typo.b2.style(
            color = UI.colors.pureInverse,
            fontWeight = FontWeight.ExtraBold,
        )
    )
}

@Composable
private fun Explain(text: String) {
    Text(
        modifier = Modifier.padding(horizontal = 24.dp),
        text = text,
        style = UI.typo.c.style(color = Gray)
    )
}

@Composable
private fun RowCard(
    label: String,
    value: String,
    onClick: () -> Unit,
    valueColor: Color = Gray,
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
            style = UI.typo.b2.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.Bold,
            )
        )
        Text(
            text = value,
            style = UI.typo.b2.style(color = valueColor, fontWeight = FontWeight.SemiBold)
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
            .background(UI.colors.medium)
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = option.name,
            style = UI.typo.b2.style(
                color = UI.colors.pureInverse,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
            )
        )
        Text(
            text = if (selected) "Shared" else option.currency,
            style = UI.typo.c.style(
                color = if (selected) Green else Gray,
                fontWeight = FontWeight.Bold,
            )
        )
    }
}

@Composable
private fun EntryRow(
    entry: PotEntry,
    currency: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = UI.typo.b2.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.SemiBold,
                )
            )
            Text(
                text = entry.timeLabel,
                style = UI.typo.c.style(color = Gray)
            )
        }

        Text(
            text = "${entry.amount.format(currency)} $currency",
            style = UI.typo.b2.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.ExtraBold,
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
