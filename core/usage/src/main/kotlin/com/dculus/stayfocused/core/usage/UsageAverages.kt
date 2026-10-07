package com.dculus.stayfocused.core.usage

import java.time.LocalDate

/** Daily totals of one complete past day, as read from the cache. */
data class DayTotals(
    val date: LocalDate,
    val totalMillis: Long,
    val opens: Int,
    val unlocks: Int,
)

/**
 * Means over the previous complete days that have data ([daysCounted] of them, at most [AVERAGE_WINDOW_DAYS]).
 * A user with no complete day yet gets [NONE] (all zeros, `hasData == false`).
 */
data class UsageAverages(
    val daysCounted: Int,
    val avgTotalMillis: Long,
    val avgOpens: Int,
    val avgUnlocks: Int,
) {
    val hasData: Boolean get() = daysCounted > 0

    /** The flat "average" line of the hourly chart: the average day spread over 24 hours. */
    val avgHourlyMillis: Long get() = avgTotalMillis / HOURS_PER_DAY

    val avgTotalMins: Int get() = millisToMins(avgTotalMillis)

    companion object {
        const val AVERAGE_WINDOW_DAYS = 7

        val NONE = UsageAverages(daysCounted = 0, avgTotalMillis = 0, avgOpens = 0, avgUnlocks = 0)

        /** Pure: averages [days] as given (the caller supplies only complete days that have data). */
        fun of(days: List<DayTotals>): UsageAverages {
            if (days.isEmpty()) return NONE
            val n = days.size
            return UsageAverages(
                daysCounted = n,
                avgTotalMillis = days.sumOf { it.totalMillis } / n,
                avgOpens = roundedMean(days.sumOf { it.opens }, n),
                avgUnlocks = roundedMean(days.sumOf { it.unlocks }, n),
            )
        }

        private fun roundedMean(
            sum: Int,
            n: Int,
        ): Int = (sum + n / 2) / n
    }
}

/** Opens of the day across all apps. */
val DayUsageStats.totalOpens: Int get() = apps.sumOf { it.opens }
