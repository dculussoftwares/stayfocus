package com.dculus.stayfocused.core.sync

private const val HOURS_PER_DAY = 24
private const val MAX_HOUR_MINS = 60

/** Today's `usage/{date}` doc as the parent reads it; fields a Kids build does not report yet stay null. */
internal data class TodayUsage(
    val mins: Int?,
    val hourly: List<Int>?,
)

/** The 24 per-hour minutes, each clamped to 0..60; null when absent or not exactly 24 numbers. */
internal fun Any?.toHourlyMins(): List<Int>? =
    (this as? List<*>)
        ?.takeIf { it.size == HOURS_PER_DAY }
        ?.mapNotNull { it as? Number }
        ?.takeIf { it.size == HOURS_PER_DAY }
        ?.map { it.toInt().coerceIn(0, MAX_HOUR_MINS) }

internal fun todayUsageFrom(data: Map<String, Any?>?): TodayUsage =
    TodayUsage(
        mins = (data?.get("totalMins") as? Number)?.toInt(),
        hourly = data?.get("hourly").toHourlyMins(),
    )
