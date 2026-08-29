package com.ivy.widget.quickadd

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.ivy.domain.di.QuickAddEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * Shared widget state and helpers for the quick-add widgets.
 *
 * A widget can't hold a snackbar, so the "Saved ₹60 · Undo" confirmation *is* widget state: it
 * is written when a transaction is created and cleared when the undo window lapses.
 */
object QuickAddWidgetState {
    val SAVED_TRANSACTION_ID = stringPreferencesKey("quick_add_saved_transaction_id")
    val SAVED_TEXT = stringPreferencesKey("quick_add_saved_text")
    val SAVED_AT_EPOCH_MILLIS = longPreferencesKey("quick_add_saved_at")

    // Keypad widget
    val AMOUNT = stringPreferencesKey("quick_add_keypad_amount")
    val TYPE = stringPreferencesKey("quick_add_keypad_type")
    val CATEGORY_ID = stringPreferencesKey("quick_add_keypad_category_id")
    val ACCOUNT_ID = stringPreferencesKey("quick_add_keypad_account_id")

    /**
     * How long "Undo" stays honest. After this the banner is stale - undoing then would delete
     * something the user has long stopped thinking about - so the action refuses and clears.
     */
    const val UNDO_WINDOW_MILLIS = 5 * 60 * 1000L

    /** How long the confirmation sits on the widget before it tidies itself away. */
    const val BANNER_MILLIS = 5_000L

    fun entryPoint(context: Context): QuickAddEntryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        QuickAddEntryPoint::class.java
    )

    suspend fun edit(
        context: Context,
        glanceId: GlanceId,
        widget: GlanceAppWidget,
        block: (MutablePreferences) -> Unit,
    ) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply { block(this) }
        }
        widget.update(context, glanceId)
    }

    fun MutablePreferences.clearSaved() {
        remove(SAVED_TRANSACTION_ID)
        remove(SAVED_TEXT)
        remove(SAVED_AT_EPOCH_MILLIS)
    }
}
