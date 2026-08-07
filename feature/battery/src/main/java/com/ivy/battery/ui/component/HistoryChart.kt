package com.ivy.battery.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.ivy.battery.ui.HistoryPoint
import com.ivy.battery.ui.format.formatClock

/**
 * A compact time-series chart for the battery history.
 *
 * Segments recorded while the charger was connected are drawn in a second
 * colour, which turns the level graph into a charge/discharge timeline without
 * needing a separate legend or a second chart.
 */
@Composable
fun HistoryChart(
    points: List<HistoryPoint>,
    lineColor: Color,
    chargingColor: Color,
    modifier: Modifier = Modifier,
    minValue: Float? = null,
    maxValue: Float? = null,
    valueFormatter: (Float) -> String = { it.toInt().toString() },
    highlightCharging: Boolean = true,
) {
    if (points.size < 2) {
        EmptyState(
            title = "Not enough data yet",
            message = "Keep monitoring running for a while and the graph will fill in.",
            modifier = modifier,
        )
        return
    }

    val values = points.map { it.value }
    val lowest = minValue ?: values.min()
    val highest = maxValue ?: values.max()
    val span = (highest - lowest).takeIf { it > 0.001f } ?: 1f

    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.height(CHART_HEIGHT.dp).padding(end = 8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                AxisLabel(valueFormatter(highest))
                AxisLabel(valueFormatter(lowest + span / 2f))
                AxisLabel(valueFormatter(lowest))
            }

            Box(modifier = Modifier.weight(1f)) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CHART_HEIGHT.dp)
                ) {
                    drawGrid(gridColor)
                    drawSeries(
                        points = points,
                        lowest = lowest,
                        span = span,
                        lineColor = lineColor,
                        chargingColor = chargingColor,
                        highlightCharging = highlightCharging,
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(start = 32.dp)) {
            AxisLabel(formatClock(points.first().timestamp))
            Spacer(Modifier.weight(1f))
            AxisLabel(formatClock(points[points.size / 2].timestamp))
            Spacer(Modifier.weight(1f))
            AxisLabel(formatClock(points.last().timestamp))
        }
    }
}

@Composable
private fun AxisLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun DrawScope.drawGrid(gridColor: Color) {
    val lines = 4
    repeat(lines + 1) { index ->
        val y = size.height * index / lines
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
        )
    }
}

@Suppress("LongParameterList")
private fun DrawScope.drawSeries(
    points: List<HistoryPoint>,
    lowest: Float,
    span: Float,
    lineColor: Color,
    chargingColor: Color,
    highlightCharging: Boolean,
) {
    val first = points.first().timestamp
    val last = points.last().timestamp
    val timeSpan = (last - first).takeIf { it > 0 } ?: 1L

    fun xFor(timestamp: Long): Float =
        size.width * ((timestamp - first).toFloat() / timeSpan.toFloat())

    fun yFor(value: Float): Float =
        size.height * (1f - ((value - lowest) / span)).coerceIn(0f, 1f)

    // Soft fill under the curve, so the chart reads at a glance.
    val fillPath = Path().apply {
        moveTo(xFor(points.first().timestamp), size.height)
        points.forEach { lineTo(xFor(it.timestamp), yFor(it.value)) }
        lineTo(xFor(points.last().timestamp), size.height)
        close()
    }
    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            listOf(lineColor.copy(alpha = 0.28f), Color.Transparent)
        ),
    )

    points.zipWithNext { a, b ->
        val color = if (highlightCharging && a.charging) chargingColor else lineColor
        drawLine(
            color = color,
            start = Offset(xFor(a.timestamp), yFor(a.value)),
            end = Offset(xFor(b.timestamp), yFor(b.value)),
            strokeWidth = STROKE_WIDTH,
            cap = StrokeCap.Round,
        )
    }
}

private const val CHART_HEIGHT = 160
private const val STROKE_WIDTH = 4f
