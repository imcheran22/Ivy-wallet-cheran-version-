package com.ivy.battery.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun BatteryTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AccuDarkScheme,
        shapes = AccuShapes,
        content = content,
    )
}

private val AccuShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val AccuDarkScheme = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    onPrimary = Color(0xFF003544),
    primaryContainer = Color(0xFF004D64),
    onPrimaryContainer = Color(0xFFB3E5FC),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF1E3A36),
    onSecondaryContainer = Color(0xFFB2DFDB),
    tertiary = Color(0xFFA5D6A7),
    onTertiary = Color(0xFF0D3710),
    tertiaryContainer = Color(0xFF1B5E20),
    onTertiaryContainer = Color(0xFFC8E6C9),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111418),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF232830),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF181C22),
    onSurfaceVariant = Color(0xFF9AA0A6),
    outline = Color(0xFF3C4043),
    outlineVariant = Color(0xFF2D3238),
)

object BatteryLevelColors {
    val Critical = Color(0xFFEF5350)
    val Low = Color(0xFFFFB74D)
    val Good = Color(0xFF66BB6A)
    val Charging = Color(0xFF4FC3F7)

    fun forLevel(level: Int, charging: Boolean): Color = when {
        charging -> Charging
        level <= 15 -> Critical
        level <= 30 -> Low
        else -> Good
    }
}
