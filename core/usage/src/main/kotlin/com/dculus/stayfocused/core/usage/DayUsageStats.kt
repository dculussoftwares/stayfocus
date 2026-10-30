package com.dculus.stayfocused.core.usage

import com.dculus.stayfocused.core.model.DayUsage
import java.time.LocalDate

const val HOURS_PER_DAY = 24

private const val MILLIS_PER_MINUTE = 60_000L
private const val MAX_HOUR_MINS = 120

/** Usage of one app on one day. */
data class AppUsageStat(
    val pkg: String,
    val millis: Long,
    val opens: Int,
    /** How many unlocks of the day had this app as the first one opened (Insights, Unlocks tab). */
    val firstAfterUnlock: Int,
    /** When the app last left the foreground that day (epoch millis); 0 when unknown (older cached days). */
    val lastUsedMillis: Long = 0,
    /** How long that last stretch in the foreground lasted; 0 when unknown. */
    val lastSessionMillis: Long = 0,
) {
    val mins: Int get() = millisToMins(millis)
}

/** Everything the Insights and Home screens need about one day. Own apps, launchers and system UI are excluded. */
data class DayUsageStats(
    val date: LocalDate,
    val totalMillis: Long,
    /** Busiest first. */
    val apps: List<AppUsageStat>,
    /** Foreground milliseconds per local hour of day (24 entries). */
    val hourlyMillis: List<Long>,
    val unlocks: Int,
    /** Unlocks per local hour of day (24 entries). */
    val hourlyUnlocks: List<Int>,
) {
    val totalMins: Int get() = millisToMins(totalMillis)

    /** [hourlyMillis] in whole minutes, each capped at 120 (a repeated local hour on a DST fall-back day holds two). */
    val hourlyMins: List<Int> get() = hourlyMillis.map { millisToMins(it).coerceIn(0, MAX_HOUR_MINS) }

    fun toDayUsage(): DayUsage = DayUsage(date = date, totalMins = totalMins)

    companion object {
        fun empty(date: LocalDate) =
            DayUsageStats(
                date = date,
                totalMillis = 0,
                apps = emptyList(),
                hourlyMillis = List(HOURS_PER_DAY) { 0L },
                unlocks = 0,
                hourlyUnlocks = List(HOURS_PER_DAY) { 0 },
            )
    }
}

internal fun millisToMins(millis: Long): Int = ((millis + MILLIS_PER_MINUTE / 2) / MILLIS_PER_MINUTE).toInt()
