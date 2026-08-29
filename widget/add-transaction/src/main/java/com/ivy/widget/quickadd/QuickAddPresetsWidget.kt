package com.ivy.widget.quickadd

import android.content.Context
import android.content.Intent
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
import com.ivy.domain.usecase.quickadd.QuickAddPreset
import com.ivy.widget.quickadd.QuickAddWidgetState.SAVED_TEXT
import com.ivy.widget.quickadd.QuickAddWidgetState.entryPoint
import com.ivy.widgets.WidgetBase

/**
 * One-tap presets on the home screen.
 *
 * The transactions people fail to record are the small repeated ones - the chai, the auto ride -
 * and the reason is never that the app is slow to open, it's that opening anything at all is more
 * effort than the spend deserves. So this widget asks for nothing: the amount, category and
 * account were decided once, in Settings, and every logging after that is a single tap with an
 * undo attached.
 */
class QuickAddPresetsWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<Preferences>
        get() = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = entryPoint(context)
        val presets = runCatching { entry.quickAddPresetStore().all() }
            .getOrDefault(emptyList())
        val currency = runCatching { entry.quickAddOptionsUseCase().load().defaultAccount }
            .getOrNull()?.assetCode.orEmpty()
        val expenseIntent = entry.appStarter().getQuickAddIntent(TransactionType.EXPENSE)
        val incomeIntent = entry.appStarter().getQuickAddIntent(TransactionType.INCOME)

        provideContent {
            val prefs = currentState<Preferences>()
            QuickAddPresetsContent(
                presets = presets,
                currency = currency,
                savedText = prefs[SAVED_TEXT],
                expenseIntent = expenseIntent,
                incomeIntent = incomeIntent,
            )
        }
    }

    companion object {
        fun presetRows(presets: List<QuickAddPreset>): List<List<QuickAddPreset>> =
            presets.chunked(PRESETS_PER_ROW)

        const val PRESETS_PER_ROW = 2
    }
}

@Keep
class QuickAddPresetsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddPresetsWidget()

    companion object {
        fun updateBroadcast(context: Context) {
            WidgetBase.updateBroadcast(context, QuickAddPresetsWidgetReceiver::class.java)
        }
    }
}
