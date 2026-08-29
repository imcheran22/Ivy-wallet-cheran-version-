package com.ivy.widget.budget

import android.content.Context
import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
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
import com.ivy.domain.di.WidgetDataEntryPoint
import com.ivy.ui.R
import com.ivy.widgets.WidgetBase
import dagger.hilt.android.EntryPointAccessors
import java.text.DecimalFormat

/**
 * How much is left, and how much of it is safe to spend today.
 *
 * Budgets already existed in the app but were invisible until you opened it, which is exactly
 * backwards: the moment the number would change a decision is the moment you're standing in a
 * shop, looking at your home screen.
 */
class LeftToSpendWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetDataEntryPoint::class.java
        )
        val progress = runCatching { entry.budgetProgressUseCase().load() }.getOrNull()
        val openIntent = entry.appStarter().getRootIntent()

        provideContent {
            val resources = LocalContext.current.resources
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ImageProvider(R.drawable.shape_widget_background))
                    .clickable(actionStartActivity(openIntent))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (progress == null || !progress.hasBudgets) {
                    Text(
                        modifier = GlanceModifier.fillMaxWidth(),
                        text = resources.getString(R.string.no_budgets_yet_widget),
                        style = TextStyle(
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            color = ColorProvider(Color.White),
                        ),
                    )
                } else {
                    LeftToSpendContent(
                        remaining = progress.remaining,
                        safeToday = progress.safeToSpendToday,
                        currency = progress.currency,
                        daysLeft = progress.daysLeft,
                        overspent = progress.remaining < 0,
                    )
                }
            }
        }
    }
}

@Composable
private fun LeftToSpendContent(
    remaining: Double,
    safeToday: Double,
    currency: String,
    daysLeft: Int,
    overspent: Boolean,
) {
    val resources = LocalContext.current.resources
    val format = DecimalFormat("###,###.##")

    Text(
        text = resources.getString(R.string.left_to_spend),
        style = TextStyle(fontSize = 12.sp, color = ColorProvider(Color.White)),
    )
    Spacer(GlanceModifier.height(2.dp))
    Text(
        text = "${format.format(remaining)} $currency",
        style = TextStyle(
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = ColorProvider(if (overspent) OVERSPENT else Color.White),
        ),
    )
    Spacer(GlanceModifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = resources.getString(
                R.string.safe_to_spend_today,
                "${format.format(maxOf(0.0, safeToday))} $currency"
            ),
            style = TextStyle(fontSize = 13.sp, color = ColorProvider(Color.White)),
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = resources.getQuantityString(R.plurals.days_left, daysLeft, daysLeft),
            style = TextStyle(fontSize = 11.sp, color = ColorProvider(Color.White)),
        )
    }
}

private val OVERSPENT = Color(0xFFFF4060)

@Keep
class LeftToSpendWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LeftToSpendWidget()

    companion object {
        fun updateBroadcast(context: Context) {
            WidgetBase.updateBroadcast(context, LeftToSpendWidgetReceiver::class.java)
        }
    }
}
