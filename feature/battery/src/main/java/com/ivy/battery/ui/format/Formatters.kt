package com.ivy.battery.ui.format

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val SECOND_MS = 1000L
private const val MINUTE_MS = 60L * SECOND_MS
private const val HOUR_MS = 60L * MINUTE_MS
private const val DAY_MS = 24L * HOUR_MS

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
private val dateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault())
private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

/**
 * "2 d 4 hr", "1 hr 5 min", "45 min", "30 sec" - always at most two units, so
 * it stays readable inside a stat tile.
 */
fun formatDuration(millis: Long): String {
    val ms = abs(millis)
    return when {
        ms >= DAY_MS -> {
            val days = ms / DAY_MS
            val hours = (ms % DAY_MS) / HOUR_MS
            if (hours > 0) "$days d $hours hr" else "$days d"
        }

        ms >= HOUR_MS -> {
            val hours = ms / HOUR_MS
            val minutes = (ms % HOUR_MS) / MINUTE_MS
            if (minutes > 0) "$hours hr $minutes min" else "$hours hr"
        }

        ms >= MINUTE_MS -> "${ms / MINUTE_MS} min"
        else -> "${ms / SECOND_MS} sec"
    }
}

/** Compact variant for chart axes and dense rows. */
fun formatDurationShort(millis: Long): String {
    val ms = abs(millis)
    return when {
        ms >= DAY_MS -> "%.1fd".format(Locale.US, ms / DAY_MS.toDouble())
        ms >= HOUR_MS -> "%.1fh".format(Locale.US, ms / HOUR_MS.toDouble())
        ms >= MINUTE_MS -> "${ms / MINUTE_MS}m"
        else -> "${ms / SECOND_MS}s"
    }
}

fun formatCurrent(milliAmps: Double): String = when {
    abs(milliAmps) >= 1000 -> "%.2f A".format(Locale.US, milliAmps / 1000.0)
    else -> "${milliAmps.roundToInt()} mA"
}

fun formatWatts(watts: Double): String = "%.2f W".format(Locale.US, watts)

fun formatVoltage(milliVolts: Int): String = "%.2f V".format(Locale.US, milliVolts / 1000.0)

fun formatTemperature(celsius: Float, fahrenheit: Boolean): String = if (fahrenheit) {
    "%.1f°F".format(Locale.US, celsius * 9f / 5f + 32f)
} else {
    "%.1f°C".format(Locale.US, celsius)
}

fun formatMah(mah: Double): String = "${mah.roundToInt()} mAh"

fun formatPercent(value: Double, decimals: Int = 1): String =
    "%.${decimals}f%%".format(Locale.US, value)

fun formatPercentPerHour(value: Double): String = "%.1f%%/h".format(Locale.US, value)

fun formatClock(epochMs: Long): String =
    timeFormatter.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

fun formatDateTime(epochMs: Long): String =
    dateTimeFormatter.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

fun formatDay(epochMs: Long): String =
    dayFormatter.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
