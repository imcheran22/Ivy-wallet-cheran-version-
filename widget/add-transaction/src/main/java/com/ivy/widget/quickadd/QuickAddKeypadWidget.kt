package com.ivy.widget.quickadd

import android.content.Context
import androidx.annotation.Keep
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.ivy.base.model.TransactionType
import com.ivy.widget.quickadd.QuickAddWidgetState.ACCOUNT_ID
import com.ivy.widget.quickadd.QuickAddWidgetState.AMOUNT
import com.ivy.widget.quickadd.QuickAddWidgetState.CATEGORY_ID
import com.ivy.widget.quickadd.QuickAddWidgetState.SAVED_TEXT
import com.ivy.widget.quickadd.QuickAddWidgetState.TYPE
import com.ivy.widget.quickadd.QuickAddWidgetState.entryPoint
import com.ivy.widgets.WidgetBase

/**
 * A full number pad on the home screen.
 *
 * RemoteViews has no text field, so "typing" into a widget has to be built out of buttons -
 * every press is a broadcast, a state write and a re-render. That costs a beat per digit, which
 * is why the preset widget exists for the amounts you repeat; this one is for the ones you don't,
 * when you still would rather not leave your home screen.
 */
class QuickAddKeypadWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<Preferences>
        get() = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = entryPoint(context)
        val options = runCatching { entry.quickAddOptionsUseCase().load() }.getOrNull()
        val accounts = options?.accounts.orEmpty()
        val categories = options?.categories.orEmpty()
        val fullEditorIntent = entry.appStarter().getQuickAddIntent(TransactionType.EXPENSE)

        provideContent {
            val prefs = currentState<Preferences>()
            val selectedAccount = accounts.firstOrNull { it.id.toString() == prefs[ACCOUNT_ID] }
                ?: accounts.firstOrNull()
            val selectedCategory = categories.firstOrNull { it.id.toString() == prefs[CATEGORY_ID] }

            QuickAddKeypadContent(
                amount = prefs[AMOUNT].orEmpty(),
                type = runCatching { TransactionType.valueOf(prefs[TYPE].orEmpty()) }
                    .getOrDefault(TransactionType.EXPENSE),
                currency = selectedAccount?.assetCode.orEmpty(),
                accountName = selectedAccount?.name,
                categoryName = selectedCategory?.name,
                savedText = prefs[SAVED_TEXT],
                fullEditorIntent = fullEditorIntent,
            )
        }
    }
}

@Keep
class QuickAddKeypadWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddKeypadWidget()

    companion object {
        fun updateBroadcast(context: Context) {
            WidgetBase.updateBroadcast(context, QuickAddKeypadWidgetReceiver::class.java)
        }
    }
}
