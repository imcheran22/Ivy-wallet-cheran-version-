package com.ivy.domain.usecase.trip

import java.time.LocalDate
import java.util.UUID

/**
 * A named stretch of time - a holiday, a work trip, a wedding.
 *
 * Deliberately defined by dates rather than by tagging each transaction: on a trip you are the
 * least likely to be labelling anything, and the dates are the one thing you'll still remember
 * afterwards. Anything spent inside the window belongs to the trip.
 */
data class Trip(
    val id: UUID,
    val name: String,
    val startDate: LocalDate,
    val endDateInclusive: LocalDate,
    /** Empty means every account - most people spend from several while travelling. */
    val accountIds: List<UUID> = emptyList(),
    /** The currency spent on the ground, shown alongside the home-currency total. */
    val currency: String? = null,
) {
    val days: Int
        get() = (endDateInclusive.toEpochDay() - startDate.toEpochDay()).toInt() + 1

    fun contains(date: LocalDate): Boolean =
        !date.isBefore(startDate) && !date.isAfter(endDateInclusive)
}
