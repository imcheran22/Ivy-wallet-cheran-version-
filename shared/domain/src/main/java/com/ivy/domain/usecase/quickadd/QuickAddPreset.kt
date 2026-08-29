package com.ivy.domain.usecase.quickadd

import com.ivy.base.model.TransactionType
import java.util.UUID

/**
 * A one-tap shortcut for a spend the user makes over and over: the ₹20 chai, the ₹60 auto ride.
 *
 * The amount, the category and the account are all decided up front, so logging one is a single
 * tap from the home screen with nothing left to type. That is the whole point - the transactions
 * people fail to record are the small repeated ones, and every field you ask for at the moment
 * of spending is another reason not to bother.
 *
 * [accountId] and [categoryId] are nullable so a preset keeps working after the user deletes the
 * account or category it pointed at: a null account falls back to the primary account rather
 * than making the preset dead weight on the home screen.
 */
data class QuickAddPreset(
    val id: UUID,
    val label: String,
    val amount: Double,
    val type: TransactionType,
    val categoryId: UUID?,
    val accountId: UUID?,
    val orderNum: Double,
) {
    companion object {
        const val MAX_PRESETS = 12
    }
}
