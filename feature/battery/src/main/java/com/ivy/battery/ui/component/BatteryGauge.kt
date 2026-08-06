package com.ivy.battery.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val START_ANGLE = 135f
private const val SWEEP_ANGLE = 270f

@Composable
fun BatteryGauge(
    level: Int,
    charging: Boolean,
    accentColor: Color,
    statusLabel: String,
    detailLabel: String?,
    modifier: Modifier = Modifier,
) {
    val animatedLevel by animateFloatAsState(
        targetValue = level.coerceIn(0, 100) / 100f,
        animationSpec = tween(durationMillis = 700),
        label = "battery level",
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.15f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(36.dp)) {
            val stroke = size.minDimension * 0.06f
            val glowWidth = stroke * 2.8f
            val halfGlow = glowWidth / 2f
            val arcSize = Size(size.width - glowWidth, size.height - glowWidth)
            val topLeft = Offset(halfGlow, halfGlow)

            drawArc(
                color = trackColor,
                startAngle = START_ANGLE,
                sweepAngle = SWEEP_ANGLE,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            if (animatedLevel > 0f) {
                drawArc(
                    color = accentColor.copy(alpha = 0.12f),
                    startAngle = START_ANGLE,
                    sweepAngle = SWEEP_ANGLE * animatedLevel,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = glowWidth, cap = StrokeCap.Round),
                )

                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            accentColor.copy(alpha = 0.5f),
                            accentColor,
                            accentColor,
                        )
                    ),
                    startAngle = START_ANGLE,
                    sweepAngle = SWEEP_ANGLE * animatedLevel,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (charging) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Text(
                    text = "$level",
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "%",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Spacer(Modifier.height(2.dp))

            Text(
                text = statusLabel,
                style = MaterialTheme.typography.titleSmall,
                color = accentColor,
                textAlign = TextAlign.Center,
            )

            if (detailLabel != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = detailLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 48.dp),
                )
            }
        }
    }
}
