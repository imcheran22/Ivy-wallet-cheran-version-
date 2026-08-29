package com.ivy.widget.quickadd

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.ivy.base.model.TransactionType
import com.ivy.widget.quickadd.QuickAddWidgetState.ACCOUNT_ID
import com.ivy.widget.quickadd.QuickAddWidgetState.AMOUNT
import com.ivy.widget.quickadd.QuickAddWidgetState.CATEGORY_ID
import com.ivy.widget.quickadd.QuickAddWidgetState.TYPE
import com.ivy.widget.quickadd.QuickAddWidgetState.clearSaved
import com.ivy.widget.quickadd.QuickAddWidgetState.edit
import com.ivy.widget.quickadd.QuickAddWidgetState.entryPoint

const val KEY_BACKSPACE = "del"
const val KEY_DECIMAL = "."
private const val MAX_DECIMALS = 2
private const val MAX_DIGITS = 12

/**
 * A keypad press on the widget itself.
 *
 * Every press is a round trip through the widget host, so the amount lives in widget state
 * rather than in memory - the process that handled "6" may well be gone by the time "0" arrives.
 */
class KeypadDigitAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val symbol = parameters[QuickAddActionKeys.DIGIT] ?: return
        val current = readState(context, glanceId)[AMOUNT].orEmpty()

        edit(context, glanceId, QuickAddKeypadWidget()) {
            it[AMOUNT] = applyKey(current, symbol)
            it.clearSaved()
        }
    }
}

/** Flips between expense and income without leaving the home screen. */
class KeypadToggleTypeAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val requested = parameters[QuickAddActionKeys.TRANSACTION_TYPE]
        edit(context, glanceId, QuickAddKeypadWidget()) {
            it[TYPE] = requested ?: TransactionType.EXPENSE.name
        }
    }
}

/**
 * Steps to the next category.
 *
 * A widget has no room for a picker and no way to open one, so the category chip cycles:
 * tap it until it says what you meant. Tapping past the last one lands on "no category",
 * which is a real answer rather than a gap.
 */
class KeypadCycleCategoryAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val categories = runCatching { entryPoint(context).quickAddOptionsUseCase().load() }
            .getOrNull()?.categories.orEmpty()
        if (categories.isEmpty()) return

        val currentId = readState(context, glanceId)[CATEGORY_ID]
        val currentIndex = categories.indexOfFirst { it.id.toString() == currentId }
        val next = categories.getOrNull(currentIndex + 1)

        edit(context, glanceId, QuickAddKeypadWidget()) {
            if (next == null) {
                it.remove(CATEGORY_ID)
            } else {
                it[CATEGORY_ID] = next.id.toString()
            }
        }
    }
}

/** Steps to the next account, same reasoning as the category chip. */
class KeypadCycleAccountAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val accounts = runCatching { entryPoint(context).quickAddOptionsUseCase().load() }
            .getOrNull()?.accounts.orEmpty()
        if (accounts.isEmpty()) return

        val currentId = readState(context, glanceId)[ACCOUNT_ID]
        val currentIndex = accounts.indexOfFirst { it.id.toString() == currentId }
        val next = accounts[(currentIndex + 1).mod(accounts.size)]

        edit(context, glanceId, QuickAddKeypadWidget()) {
            it[ACCOUNT_ID] = next.id.toString()
        }
    }
}

/** Writes what's on the keypad, then hands back an undo. */
class KeypadSaveAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val state = readState(context, glanceId)
        val amount = state[AMOUNT]?.toDoubleOrNull() ?: return
        if (amount <= 0.0) return

        val type = runCatching { TransactionType.valueOf(state[TYPE].orEmpty()) }
            .getOrDefault(TransactionType.EXPENSE)

        val result = entryPoint(context).quickAddTransactionUseCase().add(
            type = type,
            amount = amount,
            accountId = state[ACCOUNT_ID]?.toUuidOrNull(),
            categoryId = state[CATEGORY_ID]?.toUuidOrNull(),
        )

        edit(context, glanceId, QuickAddKeypadWidget()) { it.remove(AMOUNT) }

        showSavedThenClear(
            context = context,
            glanceId = glanceId,
            widget = QuickAddKeypadWidget(),
            result = result,
        )
    }
}

/**
 * Applies one keypress to the amount being typed.
 *
 * Kept as a pure function so the rules - one decimal point, at most two decimals, no runaway
 * length - are testable without a widget host.
 */
@Suppress("ReturnCount")
internal fun applyKey(current: String, symbol: String): String {
    if (symbol == KEY_BACKSPACE) return current.dropLast(1)

    if (symbol == KEY_DECIMAL) {
        if (current.contains(KEY_DECIMAL)) return current
        return if (current.isEmpty()) "0$KEY_DECIMAL" else current + KEY_DECIMAL
    }

    if (current.length >= MAX_DIGITS) return current

    val decimals = current.substringAfter(KEY_DECIMAL, "")
    if (current.contains(KEY_DECIMAL) && decimals.length >= MAX_DECIMALS) return current

    // "0" only means something in front of a decimal point.
    if (current == "0") return symbol

    return current + symbol
}
