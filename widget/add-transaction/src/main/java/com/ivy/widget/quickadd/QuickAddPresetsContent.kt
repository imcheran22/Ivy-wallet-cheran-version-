package com.ivy.widget.quickadd

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ivy.base.model.TransactionType
import com.ivy.domain.usecase.quickadd.QuickAddPreset
import com.ivy.ui.R

@Composable
fun QuickAddPresetsContent(
    presets: List<QuickAddPreset>,
    currency: String,
    savedText: String?,
    expenseIntent: Intent,
    incomeIntent: Intent,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.shape_widget_background))
            .padding(12.dp),
    ) {
        Header(expenseIntent = expenseIntent, incomeIntent = incomeIntent)

        Spacer(GlanceModifier.height(8.dp))

        when {
            savedText != null -> SavedBanner(savedText)
            presets.isEmpty() -> EmptyState()
            else -> PresetGrid(presets = presets, currency = currency)
        }
    }
}

@Composable
private fun Header(expenseIntent: Intent, incomeIntent: Intent) {
    val resources = LocalContext.current.resources
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = resources.getString(R.string.quick_add),
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = ColorProvider(Color.White),
            ),
        )
        Spacer(GlanceModifier.defaultWeight())
        HeaderButton(
            label = resources.getString(R.string.expense),
            background = R.drawable.widget_preset_expense,
            intent = expenseIntent,
        )
        Spacer(GlanceModifier.width(6.dp))
        HeaderButton(
            label = resources.getString(R.string.income),
            background = R.drawable.widget_preset_income,
            intent = incomeIntent,
        )
    }
}

@Composable
private fun HeaderButton(label: String, background: Int, intent: Intent) {
    Text(
        modifier = GlanceModifier
            .background(ImageProvider(background))
            .clickable(actionStartActivity(intent))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        text = label,
        style = TextStyle(
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = ColorProvider(Color.White),
        ),
    )
}

@Composable
private fun PresetGrid(presets: List<QuickAddPreset>, currency: String) {
    val rows = QuickAddPresetsWidget.presetRows(presets)
    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(rows.size) { index ->
            Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
                val row = rows[index]
                row.forEach { preset ->
                    PresetButton(preset = preset, currency = currency)
                    Spacer(GlanceModifier.width(6.dp))
                }
                // Keeps a lone preset on the last row the same width as the ones above it.
                repeat(QuickAddPresetsWidget.PRESETS_PER_ROW - row.size) {
                    Spacer(GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

@Composable
private fun RowScope.PresetButton(preset: QuickAddPreset, currency: String) {
    val background = when (preset.type) {
        TransactionType.INCOME -> R.drawable.widget_preset_income
        else -> R.drawable.widget_preset_expense
    }

    Column(
        modifier = GlanceModifier
            .defaultWeight()
            .background(ImageProvider(background))
            .clickable(
                actionRunCallback<SavePresetAction>(
                    actionParametersOf(
                        QuickAddActionKeys.PRESET_ID to preset.id.toString(),
                        QuickAddActionKeys.WIDGET_KIND to PRESETS_WIDGET,
                    )
                )
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = preset.label,
            maxLines = 1,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = ColorProvider(Color.White),
            ),
        )
        Text(
            text = "${formatAmount(preset.amount)} $currency",
            maxLines = 1,
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = ColorProvider(Color.White),
            ),
        )
    }
}

@Composable
private fun SavedBanner(savedText: String) {
    val resources = LocalContext.current.resources
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ImageProvider(R.drawable.widget_banner_background))
            .clickable(
                actionRunCallback<DismissSavedAction>(
                    actionParametersOf(QuickAddActionKeys.WIDGET_KIND to PRESETS_WIDGET)
                )
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = GlanceModifier.defaultWeight(),
            text = resources.getString(R.string.saved_amount, savedText),
            maxLines = 2,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = ColorProvider(Color.White),
            ),
        )
        Text(
            modifier = GlanceModifier
                .background(ImageProvider(R.drawable.widget_key_background))
                .clickable(
                    actionRunCallback<UndoQuickAddAction>(
                        actionParametersOf(QuickAddActionKeys.WIDGET_KIND to PRESETS_WIDGET)
                    )
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            text = resources.getString(R.string.undo),
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = ColorProvider(Color.White),
            ),
        )
    }
}

@Composable
private fun EmptyState() {
    val resources = LocalContext.current.resources
    Text(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 12.dp),
        text = resources.getString(R.string.quick_add_presets_description),
        style = TextStyle(
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = ColorProvider(Color.White),
        ),
    )
}
