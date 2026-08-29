package com.ivy.wallet.quickadd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.base.model.TransactionType
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.domain.usecase.quickadd.QuickAddPreset
import com.ivy.legacy.utils.format
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Black
import com.ivy.wallet.ui.theme.Gradient
import com.ivy.wallet.ui.theme.GradientGreen
import com.ivy.wallet.ui.theme.GradientRed
import com.ivy.wallet.ui.theme.Gray
import com.ivy.wallet.ui.theme.Green
import com.ivy.wallet.ui.theme.Red
import com.ivy.wallet.ui.theme.White
import com.ivy.wallet.ui.theme.components.IvyButton
import com.ivy.wallet.ui.theme.components.IvyOutlinedButton
import com.ivy.wallet.ui.theme.components.ItemIconSDefaultIcon
import com.ivy.wallet.ui.theme.findContrastTextColor
import com.ivy.wallet.ui.theme.modal.edit.AmountCurrency
import com.ivy.wallet.ui.theme.modal.edit.AmountInput
import com.ivy.wallet.ui.theme.toComposeColor
import java.util.UUID

/**
 * The quick-add sheet: everything needed to log a transaction, drawn over whatever the user was
 * already looking at.
 *
 * It deliberately reuses the app's own amount keypad rather than a system keyboard - the same
 * component the full editor uses - so entering ₹60 here looks and feels identical to entering it
 * inside the app, only without the app.
 */
@Composable
fun QuickAddSheet(
    state: QuickAddUiState,
    onEvent: (QuickAddEvent) -> Unit,
    onDismiss: () -> Unit,
    onOpenFullEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Scrim(onClick = onDismiss)

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(UI.shapes.r2Top)
                .background(UI.colors.pure)
                .navigationBarsPadding()
                .padding(top = 12.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DragHandle()

            when {
                state.saved != null -> SavedConfirmation(
                    saved = state.saved,
                    onUndo = { onEvent(QuickAddEvent.Undo) },
                    onDone = onDismiss,
                )

                state.noAccounts -> NoAccounts(onOpenApp = onOpenFullEditor)

                else -> QuickAddForm(
                    state = state,
                    onEvent = onEvent,
                    onOpenFullEditor = onOpenFullEditor,
                )
            }
        }
    }
}

@Composable
private fun Scrim(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Spacer(
        modifier = Modifier
            .fillMaxSize()
            .background(Black.copy(alpha = 0.6f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
    )
}

@Composable
private fun DragHandle() {
    Spacer(
        modifier = Modifier
            .width(48.dp)
            .height(4.dp)
            .clip(UI.shapes.rFull)
            .background(UI.colors.medium)
    )
}

@Composable
private fun QuickAddForm(
    state: QuickAddUiState,
    onEvent: (QuickAddEvent) -> Unit,
    onOpenFullEditor: () -> Unit,
) {
    Spacer(Modifier.height(16.dp))

    TypeToggle(
        selected = state.type,
        onSelect = { onEvent(QuickAddEvent.SwitchType(it)) },
    )

    if (state.presets.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        PresetRow(
            presets = state.presets,
            currency = state.currency,
            onTap = { onEvent(QuickAddEvent.ApplyPreset(it)) },
        )
    }

    Spacer(Modifier.height(20.dp))

    AmountCurrency(amount = state.amount, currency = state.currency)

    Spacer(Modifier.height(16.dp))

    ChipRow {
        state.categories.forEach { category ->
            Chip(
                label = category.name,
                iconName = category.icon,
                defaultIcon = R.drawable.ic_custom_category_s,
                color = category.color.toComposeColor(),
                selected = state.selectedCategoryId == category.id,
                onClick = {
                    val next = category.id.takeIf { it != state.selectedCategoryId }
                    onEvent(QuickAddEvent.SelectCategory(next))
                },
            )
        }
    }

    if (state.accounts.size > 1) {
        Spacer(Modifier.height(8.dp))
        ChipRow {
            state.accounts.forEach { account ->
                Chip(
                    label = account.name,
                    iconName = account.icon,
                    defaultIcon = R.drawable.ic_custom_account_s,
                    color = account.color.toComposeColor(),
                    selected = state.selectedAccountId == account.id,
                    onClick = { onEvent(QuickAddEvent.SelectAccount(account.id)) },
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    AmountInput(
        currency = state.currency,
        amount = state.amount,
        setAmount = { onEvent(QuickAddEvent.SetAmount(it)) },
    )

    Spacer(Modifier.height(16.dp))

    Actions(
        state = state,
        onSave = { onEvent(QuickAddEvent.Save) },
        onOpenFullEditor = onOpenFullEditor,
    )
}

@Composable
private fun Actions(
    state: QuickAddUiState,
    onSave: () -> Unit,
    onOpenFullEditor: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IvyOutlinedButton(
            text = stringResource(R.string.more_options),
            iconStart = R.drawable.ic_edit,
            onClick = onOpenFullEditor,
        )

        Spacer(Modifier.weight(1f))

        IvyButton(
            text = stringResource(R.string.save),
            iconStart = R.drawable.ic_check,
            backgroundGradient = state.type.gradient(),
            enabled = state.canSave,
            onClick = onSave,
        )
    }
}

@Composable
private fun TypeToggle(
    selected: TransactionType,
    onSelect: (TransactionType) -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TypePill(
            label = stringResource(R.string.expense),
            selected = selected == TransactionType.EXPENSE,
            color = Red,
            onClick = { onSelect(TransactionType.EXPENSE) },
        )
        TypePill(
            label = stringResource(R.string.income),
            selected = selected == TransactionType.INCOME,
            color = Green,
            onClick = { onSelect(TransactionType.INCOME) },
        )
    }
}

@Composable
private fun TypePill(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    Text(
        modifier = Modifier
            .clip(UI.shapes.rFull)
            .background(if (selected) color else UI.colors.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        text = label,
        style = UI.typo.b2.style(
            color = if (selected) White else UI.colors.pureInverse,
            fontWeight = FontWeight.Bold,
        ),
    )
}

@Composable
private fun PresetRow(
    presets: List<QuickAddPreset>,
    currency: String,
    onTap: (UUID) -> Unit,
) {
    ChipRow {
        presets.forEach { preset ->
            Chip(
                label = "${preset.label} · ${preset.amount.format(currency)}",
                iconName = null,
                defaultIcon = R.drawable.ic_plus,
                color = preset.type.color(),
                selected = true,
                onClick = { onTap(preset.id) },
            )
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun Chip(
    label: String,
    iconName: String?,
    defaultIcon: Int,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) color else UI.colors.pure
    val contentColor = if (selected) findContrastTextColor(color) else UI.colors.pureInverse

    Row(
        modifier = Modifier
            .clip(UI.shapes.rFull)
            .background(background)
            .border(2.dp, if (selected) color else UI.colors.medium, UI.shapes.rFull)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemIconSDefaultIcon(
            modifier = Modifier.size(24.dp),
            iconName = iconName,
            defaultIcon = defaultIcon,
            tint = contentColor,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = UI.typo.c.style(color = contentColor, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun SavedConfirmation(
    saved: QuickAddSaved,
    onUndo: () -> Unit,
    onDone: () -> Unit,
) {
    Spacer(Modifier.height(24.dp))

    Text(
        text = stringResource(R.string.saved_amount, saved.amountText),
        style = UI.typo.h2.style(
            color = saved.type.color(),
            fontWeight = FontWeight.Bold,
        ),
    )

    Spacer(Modifier.height(4.dp))

    Text(
        text = listOfNotNull(saved.categoryName, saved.accountName).joinToString(" · "),
        style = UI.typo.b2.style(color = Gray, fontWeight = FontWeight.Medium),
    )

    Spacer(Modifier.height(20.dp))

    Row(
        modifier = Modifier.padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IvyOutlinedButton(
            text = stringResource(R.string.undo),
            iconStart = R.drawable.ic_delete,
            onClick = onUndo,
        )
        IvyButton(
            text = stringResource(R.string.done),
            iconStart = R.drawable.ic_check,
            backgroundGradient = saved.type.gradient(),
            onClick = onDone,
        )
    }

    Spacer(Modifier.height(8.dp))
}

@Composable
private fun NoAccounts(onOpenApp: () -> Unit) {
    Spacer(Modifier.height(24.dp))

    Text(
        modifier = Modifier.padding(horizontal = 24.dp),
        text = stringResource(R.string.quick_add_no_accounts),
        style = UI.typo.b2.style(color = UI.colors.pureInverse, fontWeight = FontWeight.Medium),
    )

    Spacer(Modifier.height(16.dp))

    IvyButton(
        text = stringResource(R.string.open_app),
        iconStart = R.drawable.ic_plus,
        onClick = onOpenApp,
    )

    Spacer(Modifier.height(8.dp))
}

private fun TransactionType.gradient(): Gradient = when (this) {
    TransactionType.INCOME -> GradientGreen
    else -> GradientRed
}

private fun TransactionType.color(): Color = when (this) {
    TransactionType.INCOME -> Green
    else -> Red
}
