package com.ivy.domain.usecase.period

import com.ivy.base.legacy.SharedPrefs
import com.ivy.base.time.TimeConverter
import com.ivy.base.time.TimeProvider
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

/**
 * One budgeting month, as the user defines it.
 *
 * Ivy lets people start their month on payday rather than on the 1st, and every number that
 * claims to be "this month" - budgets, safe-to-spend, the month-over-month comparison - has to
 * agree on that boundary or they will contradict each other on screen.
 */
data class MonthPeriod(
    val startDate: LocalDate,
    val endDateExclusive: LocalDate,
    val start: Instant,
    val endExclusive: Instant,
) {
    val totalDays: Int
        get() = ChronoUnit.DAYS.between(startDate, endDateExclusive).toInt()

    /** Days remaining including today, never below 1 so it is safe to divide by. */
    fun daysLeft(today: LocalDate): Int =
        max(1, ChronoUnit.DAYS.between(today, endDateExclusive).toInt())

    fun label(): String = "${startDate} - ${endDateExclusive.minusDays(1)}"
}

class MonthPeriodProvider @Inject constructor(
    private val sharedPrefs: SharedPrefs,
    private val timeProvider: TimeProvider,
    private val timeConverter: TimeConverter,
) {

    fun startDayOfMonth(): Int = sharedPrefs.getInt(SharedPrefs.START_DATE_OF_MONTH, 1)
        .coerceIn(1, MAX_START_DAY)

    fun current(): MonthPeriod = periodContaining(timeProvider.localDateNow())

    /** [monthsBack] periods before the current one - what rollover and comparisons look at. */
    fun previous(period: MonthPeriod, monthsBack: Long): MonthPeriod =
        periodStartingAt(period.startDate.minusMonths(monthsBack))

    fun periodContaining(date: LocalDate): MonthPeriod {
        val startDay = startDayOfMonth()
        val anchor = if (date.dayOfMonth >= startDay) date else date.minusMonths(1)
        return periodStartingAt(anchor.withDayOfMonthSafe(startDay))
    }

    private fun periodStartingAt(startDate: LocalDate): MonthPeriod {
        val start = startDate.withDayOfMonthSafe(startDayOfMonth())
        val end = start.plusMonths(1)
        return MonthPeriod(
            startDate = start,
            endDateExclusive = end,
            start = start.toInstant(),
            endExclusive = end.toInstant(),
        )
    }

    /** A month that starts on the 31st still has to start somewhere in February. */
    private fun LocalDate.withDayOfMonthSafe(day: Int): LocalDate =
        withDayOfMonth(min(day, lengthOfMonth()))

    private fun LocalDate.toInstant(): Instant = with(timeConverter) {
        atStartOfDay().toUTC()
    }

    companion object {
        const val MAX_START_DAY = 31
    }
}
