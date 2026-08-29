package com.ivy.widget.quickadd

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.ivy.base.model.TransactionType
import com.ivy.domain.usecase.quickadd.QuickAddTransactionUseCase
import com.ivy.widget.quickadd.QuickAddWidgetState.BANNER_MILLIS
import com.ivy.widget.quickadd.QuickAddWidgetState.SAVED_AT_EPOCH_MILLIS
import com.ivy.widget.quickadd.QuickAddWidgetState.SAVED_TEXT
import com.ivy.widget.quickadd.QuickAddWidgetState.SAVED_TRANSACTION_ID
import com.ivy.widget.quickadd.QuickAddWidgetState.UNDO_WINDOW_MILLIS
import com.ivy.widget.quickadd.QuickAddWidgetState.clearSaved
import com.ivy.widget.quickadd.QuickAddWidgetState.edit
import com.ivy.widget.quickadd.QuickAddWidgetState.entryPoint
import kotlinx.coroutines.delay
import java.util.UUID

object QuickAddActionKeys {
    val PRESET_ID = ActionParameters.Key<String>("quick_add_preset_id")
    val DIGIT = ActionParameters.Key<String>("quick_add_digit")
    val TRANSACTION_TYPE = ActionParameters.Key<String>("quick_add_type")
    val CATEGORY_ID = ActionParameters.Key<String>("quick_add_category_id")
    val ACCOUNT_ID = ActionParameters.Key<String>("quick_add_account_id")

    /** Which widget the tap came from, so shared actions update the right one. */
    val WIDGET_KIND = ActionParameters.Key<String>("quick_add_widget_kind")
}

const val PRESETS_WIDGET = "presets"
const val KEYPAD_WIDGET = "keypad"

/**
 * Files a preset with a single tap and leaves an undo behind.
 *
 * Nothing here opens the app: the transaction is written from the widget host's process straight
 * through the domain layer, which is the entire difference between this and the old widget.
 */
class SavePresetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val presetId = parameters[QuickAddActionKeys.PRESET_ID]?.toUuidOrNull() ?: return
        val entry = entryPoint(context)
        val preset = entry.quickAddPresetStore().findById(presetId) ?: return

        val result = entry.quickAddTransactionUseCase().add(
            type = preset.type,
            amount = preset.amount,
            accountId = preset.accountId,
            categoryId = preset.categoryId,
            title = preset.label,
        )

        showSavedThenClear(
            context = context,
            glanceId = glanceId,
            widget = QuickAddPresetsWidget(),
            result = result,
        )
    }
}

/**
 * Reverses the last thing this widget wrote.
 *
 * Refuses once the undo window has passed - a banner left on a home screen overnight is not
 * consent to delete a transaction the next morning.
 */
class UndoQuickAddAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val prefs = readState(context, glanceId)
        val transactionId = prefs[SAVED_TRANSACTION_ID]?.toUuidOrNull()
        val savedAt = prefs[SAVED_AT_EPOCH_MILLIS] ?: 0L
        val widget = widgetFor(parameters)

        if (transactionId != null && System.currentTimeMillis() - savedAt <= UNDO_WINDOW_MILLIS) {
            entryPoint(context).quickAddTransactionUseCase().undo(transactionId)
        }

        edit(context, glanceId, widget) { it.clearSaved() }
    }
}

/** Dismisses the confirmation without touching what was saved. */
class DismissSavedAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        edit(context, glanceId, widgetFor(parameters)) { it.clearSaved() }
    }
}

internal fun widgetFor(parameters: ActionParameters): GlanceAppWidget =
    when (parameters[QuickAddActionKeys.WIDGET_KIND]) {
        KEYPAD_WIDGET -> QuickAddKeypadWidget()
        else -> QuickAddPresetsWidget()
    }

internal suspend fun readState(context: Context, glanceId: GlanceId): Preferences =
    getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)

internal fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()

/**
 * Shows the outcome on the widget, then tidies it away.
 *
 * The delay runs inside the action's own execution window, which is short but comfortably longer
 * than the few seconds a confirmation is worth reading. If the process is killed first the
 * banner survives, and the timestamp check in [UndoQuickAddAction] keeps it from doing damage.
 */
internal suspend fun showSavedThenClear(
    context: Context,
    glanceId: GlanceId,
    widget: GlanceAppWidget,
    result: QuickAddTransactionUseCase.Result,
) {
    when (result) {
        is QuickAddTransactionUseCase.Result.Added -> {
            val savedText = buildString {
                append(if (result.type == TransactionType.EXPENSE) "-" else "+")
                append(formatAmount(result.amount))
                append(' ')
                append(result.assetCode)
                result.categoryName?.let {
                    append(" · ")
                    append(it)
                }
            }

            edit(context, glanceId, widget) {
                it[SAVED_TRANSACTION_ID] = result.transactionId.toString()
                it[SAVED_TEXT] = savedText
                it[SAVED_AT_EPOCH_MILLIS] = System.currentTimeMillis()
            }

            delay(BANNER_MILLIS)

            val stillShowing = readState(context, glanceId)[SAVED_TRANSACTION_ID] ==
                result.transactionId.toString()
            if (stillShowing) {
                edit(context, glanceId, widget) { it.clearSaved() }
            }
        }

        QuickAddTransactionUseCase.Result.NoAccounts,
        QuickAddTransactionUseCase.Result.InvalidAmount -> Unit
    }
}
