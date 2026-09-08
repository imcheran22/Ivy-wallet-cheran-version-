package com.ivy.transaction

import androidx.compose.runtime.Immutable
import com.ivy.legacy.datamodel.Account

/**
 * One slice of a payment that came out of more than one account - the ₹30 of a ₹100
 * shop that went by UPI while the rest was cash.
 *
 * A split is stored as the amount carved *out* of the total, not as the total itself.
 * The account chosen on the main screen keeps whatever is left over, so the headline
 * amount stays the number that was actually spent and the parts always add up to it.
 */
@Immutable
data class SplitPart(
    val account: Account,
    val amount: Double,
)
