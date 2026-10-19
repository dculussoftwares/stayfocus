package com.dculus.stayfocused.core.sync

private const val HOURS_PER_DAY = 24
private const val MAX_HOUR_MINS = 120
private const val MAX_DAY_MINS = 1440

/** Today's `usage/{date}` doc as the parent reads it; fields a Kids build does not report yet stay null. */
internal data class TodayUsage(
    val mins: Int?,
    val hourly: List<Int>?,
)

/** [this] as an `Int` when it is a whole number within [range]; null for fractions, out-of-range or non-numbers. */
private fun Any?.wholeIn(range: IntRange): Int? {
    val value = (this as? Number)?.toDouble() ?: return null
    return value.takeIf { it % 1.0 == 0.0 && it >= range.first && it <= range.last }?.toInt()
}

/** The 24 per-hour minutes (whole, 0..120); null when absent, not exactly 24 entries, or any entry is malformed. */
internal fun Any?.toHourlyMins(): List<Int>? =
    (this as? List<*>)
        ?.takeIf { it.size == HOURS_PER_DAY }
        ?.map { it.wholeIn(0..MAX_HOUR_MINS) ?: return null }

internal fun todayUsageFrom(data: Map<String, Any?>?): TodayUsage =
    TodayUsage(
        mins = data?.get("totalMins").wholeIn(0..MAX_DAY_MINS),
        hourly = data?.get("hourly").toHourlyMins(),
    )
