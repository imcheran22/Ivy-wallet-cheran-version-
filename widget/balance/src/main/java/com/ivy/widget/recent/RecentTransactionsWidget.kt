package com.ivy.widget.recent

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
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
import androidx.glance.appwidget.lazy.LazyColumn
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
import com.ivy.base.model.TransactionType
import com.ivy.domain.di.WidgetDataEntryPoint
import com.ivy.domain.usecase.recent.RecentTransactionRow
import com.ivy.ui.R
import com.ivy.widgets.WidgetBase
import dagger.hilt.android.EntryPointAccessors
import java.text.DecimalFormat

/**
 * The last few transactions, each tapping straight into its own editor.
 *
 * Its real job is catching mistakes: an auto-imported payment filed under the wrong category is
 * cheap to fix the same day and expensive to find three weeks later.
 */
class RecentTransactionsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetDataEntryPoint::class.java
        )
        val rows = runCatching { entry.recentTransactionsUseCase().load() }
            .getOrDefault(emptyList())
        val appStarter = entry.appStarter()
        val editIntents = rows.associate { row ->
            row.id to appStarter.getEditTransactionIntent(row.id, row.type)
        }

        provideContent {
            val resources = LocalContext.current.resources
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ImageProvider(R.drawable.shape_widget_background))
                    .padding(12.dp),
            ) {
                Text(
                    text = resources.getString(R.string.recent_transactions),
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(Color.White),
                    ),
                )
                Spacer(GlanceModifier.height(6.dp))

                if (rows.isEmpty()) {
                    Text(
                        modifier = GlanceModifier.fillMaxWidth(),
                        text = resources.getString(R.string.no_transactions),
                        style = TextStyle(
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = ColorProvider(Color.White),
                        ),
                    )
                } else {
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(rows.size) { index ->
                            val row = rows[index]
                            TransactionRow(
                                row = row,
                                onClick = editIntents[row.id],
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(row: RecentTransactionRow, onClick: Intent?) {
    val resources = LocalContext.current.resources
    val format = DecimalFormat("###,###.##")
    val amountColor = when (row.type) {
        TransactionType.INCOME -> INCOME
        TransactionType.EXPENSE -> EXPENSE
        TransactionType.TRANSFER -> Color.White
    }

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    GlanceModifier.clickable(actionStartActivity(onClick))
                } else {
                    GlanceModifier
                }
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = row.title.ifBlank { resources.getString(R.string.unspecified) },
                maxLines = 1,
                style = TextStyle(fontSize = 13.sp, color = ColorProvider(Color.White)),
            )
            Text(
                text = listOfNotNull(
                    row.categoryName ?: resources.getString(R.string.unspecified),
                    DateUtils.getRelativeTimeSpanString(
                        row.timeEpochMillis,
                        System.currentTimeMillis(),
                        DateUtils.DAY_IN_MILLIS,
                    ).toString(),
                ).joinToString(" · "),
                maxLines = 1,
                style = TextStyle(fontSize = 11.sp, color = ColorProvider(MUTED)),
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = "${format.format(row.amount)} ${row.assetCode}",
            maxLines = 1,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = ColorProvider(amountColor),
            ),
        )
    }
}

private val INCOME = Color(0xFF14CC9E)
private val EXPENSE = Color(0xFFFF4060)
private val MUTED = Color(0xFFB0B0B4)

@Keep
class RecentTransactionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecentTransactionsWidget()

    companion object {
        fun updateBroadcast(context: Context) {
            WidgetBase.updateBroadcast(context, RecentTransactionsWidgetReceiver::class.java)
        }
    }
}
