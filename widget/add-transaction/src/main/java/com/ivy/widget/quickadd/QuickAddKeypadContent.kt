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
import com.ivy.ui.R

private val KEY_ROWS = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf(KEY_DECIMAL, "0", KEY_BACKSPACE),
)

@Suppress("LongParameterList")
@Composable
fun QuickAddKeypadContent(
    amount: String,
    type: TransactionType,
    currency: String,
    accountName: String?,
    categoryName: String?,
    savedText: String?,
    fullEditorIntent: Intent,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.shape_widget_background))
            .padding(10.dp),
    ) {
        TypeRow(type = type, accountName = accountName)
        Spacer(GlanceModifier.height(6.dp))
        AmountRow(amount = amount, currency = currency, fullEditorIntent = fullEditorIntent)
        Spacer(GlanceModifier.height(6.dp))
        CategoryRow(categoryName = categoryName)
        Spacer(GlanceModifier.height(6.dp))
        Keypad()
        Spacer(GlanceModifier.height(6.dp))
        if (savedText != null) {
            KeypadSavedBanner(savedText = savedText)
        } else {
            SaveRow(type = type)
        }
    }
}

@Composable
private fun TypeRow(type: TransactionType, accountName: String?) {
    val resources = LocalContext.current.resources
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TypeChip(
            label = resources.getString(R.string.expense),
            selected = type == TransactionType.EXPENSE,
            target = TransactionType.EXPENSE,
            selectedBackground = R.drawable.widget_preset_expense,
        )
        Spacer(GlanceModifier.width(6.dp))
        TypeChip(
            label = resources.getString(R.string.income),
            selected = type == TransactionType.INCOME,
            target = TransactionType.INCOME,
            selectedBackground = R.drawable.widget_preset_income,
        )
        Spacer(GlanceModifier.defaultWeight())
        if (accountName != null) {
            Text(
                modifier = GlanceModifier
                    .background(ImageProvider(R.drawable.widget_key_background))
                    .clickable(
                        actionRunCallback<KeypadCycleAccountAction>(
                            actionParametersOf(QuickAddActionKeys.WIDGET_KIND to KEYPAD_WIDGET)
                        )
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                text = accountName,
                maxLines = 1,
                style = TextStyle(
                    fontSize = 12.sp,
                    color = ColorProvider(Color.White),
                ),
            )
        }
    }
}

@Composable
private fun RowScope.TypeChip(
    label: String,
    selected: Boolean,
    target: TransactionType,
    selectedBackground: Int,
) {
    Text(
        modifier = GlanceModifier
            .background(
                ImageProvider(
                    if (selected) selectedBackground else R.drawable.widget_key_background
                )
            )
            .clickable(
                actionRunCallback<KeypadToggleTypeAction>(
                    actionParametersOf(
                        QuickAddActionKeys.TRANSACTION_TYPE to target.name,
                        QuickAddActionKeys.WIDGET_KIND to KEYPAD_WIDGET,
                    )
                )
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        text = label,
        style = TextStyle(
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = ColorProvider(Color.White),
        ),
    )
}

@Composable
private fun AmountRow(amount: String, currency: String, fullEditorIntent: Intent) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity(fullEditorIntent))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = GlanceModifier.defaultWeight(),
            text = amount.ifEmpty { "0" },
            maxLines = 1,
            style = TextStyle(
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = ColorProvider(Color.White),
                textAlign = TextAlign.End,
            ),
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = currency,
            style = TextStyle(fontSize = 14.sp, color = ColorProvider(Color.White)),
        )
    }
}

@Composable
private fun CategoryRow(categoryName: String?) {
    val resources = LocalContext.current.resources
    Text(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ImageProvider(R.drawable.widget_key_background))
            .clickable(
                actionRunCallback<KeypadCycleCategoryAction>(
                    actionParametersOf(QuickAddActionKeys.WIDGET_KIND to KEYPAD_WIDGET)
                )
            )
            .padding(vertical = 6.dp),
        text = categoryName ?: resources.getString(R.string.unspecified),
        maxLines = 1,
        style = TextStyle(
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = ColorProvider(Color.White),
        ),
    )
}

@Composable
private fun Keypad() {
    KEY_ROWS.forEach { row ->
        Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp)) {
            row.forEachIndexed { index, symbol ->
                if (index > 0) {
                    Spacer(GlanceModifier.width(4.dp))
                }
                KeyButton(symbol = symbol)
            }
        }
    }
}

@Composable
private fun RowScope.KeyButton(symbol: String) {
    Text(
        modifier = GlanceModifier
            .defaultWeight()
            .background(ImageProvider(R.drawable.widget_key_background))
            .clickable(
                actionRunCallback<KeypadDigitAction>(
                    actionParametersOf(
                        QuickAddActionKeys.DIGIT to symbol,
                        QuickAddActionKeys.WIDGET_KIND to KEYPAD_WIDGET,
                    )
                )
            )
            .padding(vertical = 10.dp),
        text = if (symbol == KEY_BACKSPACE) "⌫" else symbol,
        style = TextStyle(
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = ColorProvider(Color.White),
        ),
    )
}

@Composable
private fun SaveRow(type: TransactionType) {
    val resources = LocalContext.current.resources
    Text(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(
                ImageProvider(
                    when (type) {
                        TransactionType.INCOME -> R.drawable.widget_preset_income
                        else -> R.drawable.widget_preset_expense
                    }
                )
            )
            .clickable(
                actionRunCallback<KeypadSaveAction>(
                    actionParametersOf(QuickAddActionKeys.WIDGET_KIND to KEYPAD_WIDGET)
                )
            )
            .padding(vertical = 10.dp),
        text = resources.getString(R.string.save),
        style = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = ColorProvider(Color.White),
        ),
    )
}

@Composable
private fun KeypadSavedBanner(savedText: String) {
    val resources = LocalContext.current.resources
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ImageProvider(R.drawable.widget_banner_background))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = GlanceModifier.defaultWeight(),
            text = savedText,
            maxLines = 1,
            style = TextStyle(fontSize = 12.sp, color = ColorProvider(Color.White)),
        )
        Text(
            modifier = GlanceModifier
                .background(ImageProvider(R.drawable.widget_key_background))
                .clickable(
                    actionRunCallback<UndoQuickAddAction>(
                        actionParametersOf(QuickAddActionKeys.WIDGET_KIND to KEYPAD_WIDGET)
                    )
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
            text = resources.getString(R.string.undo),
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = ColorProvider(Color.White),
            ),
        )
    }
}
