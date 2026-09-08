package com.ivy.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.datamodel.Account
import com.ivy.legacy.utils.format
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Gray
import com.ivy.wallet.ui.theme.components.IvyIcon
import kotlinx.collections.immutable.ImmutableList

/**
 * Lets one payment be settled from more than one account - ₹100 of shopping paid ₹70 in
 * cash and ₹30 by UPI.
 *
 * The headline amount stays the whole payment. Each split carves a slice out of it and the
 * account chosen on the main screen keeps the remainder, so the parts always add up to what
 * was actually spent and there is no total to keep in sync by hand.
 */
@Composable
fun SplitPaymentSection(
    total: Double,
    currency: String,
    primaryAccount: Account?,
    accounts: ImmutableList<Account>,
    splits: ImmutableList<SplitPart>,
    onPickAccount: (Account) -> Unit,
    onRemoveSplit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val splitTotal = splits.sumOf { it.amount }
    val remainder = total - splitTotal

    var pickerOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            modifier = Modifier.padding(start = 24.dp),
            text = stringResource(R.string.split_payment),
            style = UI.typo.b2.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.Bold,
            ),
        )

        Spacer(Modifier.height(4.dp))

        Text(
            modifier = Modifier.padding(horizontal = 24.dp),
            text = stringResource(R.string.split_payment_description),
            style = UI.typo.c.style(color = Gray),
        )

        Spacer(Modifier.height(12.dp))

        // The remainder is the whole point of the section: it is the number that tells you
        // the parts still describe the payment you made.
        if (splits.isNotEmpty() && primaryAccount != null) {
            SplitRow(
                accountName = primaryAccount.name,
                amount = remainder,
                currency = currency,
                onRemove = null,
            )
        }

        splits.forEachIndexed { index, part ->
            SplitRow(
                accountName = part.account.name,
                amount = part.amount,
                currency = currency,
                onRemove = { onRemoveSplit(index) },
            )
        }

        Spacer(Modifier.height(8.dp))

        // Nothing left to carve out, so offering another split would only produce an
        // invalid one.
        val canAddMore = remainder > 0.0 && accounts.size > 1

        if (canAddMore) {
            if (pickerOpen) {
                AccountPickerRow(
                    accounts = accounts.filter { it.id != primaryAccount?.id },
                    onPick = {
                        pickerOpen = false
                        onPickAccount(it)
                    },
                )
            } else {
                Text(
                    modifier = Modifier
                        .padding(start = 24.dp)
                        .clip(UI.shapes.rFull)
                        .border(2.dp, UI.colors.medium, UI.shapes.rFull)
                        .clickable { pickerOpen = true }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .testTag("add_split_button"),
                    text = stringResource(R.string.add_split),
                    style = UI.typo.b2.style(
                        color = UI.colors.pureInverse,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SplitRow(
    accountName: String,
    amount: Double,
    currency: String,
    onRemove: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = accountName,
            style = UI.typo.b2.style(color = UI.colors.pureInverse),
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = "${amount.format(currency)} $currency",
            style = UI.typo.nB2.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.Bold,
            ),
        )

        if (onRemove != null) {
            Spacer(Modifier.width(12.dp))

            IvyIcon(
                modifier = Modifier
                    .size(24.dp)
                    .clip(UI.shapes.rFull)
                    .clickable(onClick = onRemove),
                icon = R.drawable.ic_delete,
                tint = Gray,
                contentDescription = "remove_split",
            )
        }
    }
}

@Composable
private fun AccountPickerRow(
    accounts: List<Account>,
    onPick: (Account) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        accounts.forEach { acc ->
            Text(
                modifier = Modifier
                    .clip(UI.shapes.rFull)
                    .background(UI.colors.medium)
                    .clickable { onPick(acc) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                text = acc.name,
                style = UI.typo.b2.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}
