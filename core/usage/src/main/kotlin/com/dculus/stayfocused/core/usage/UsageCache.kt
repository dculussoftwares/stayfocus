package com.dculus.stayfocused.core.usage

import com.dculus.stayfocused.core.data.db.UsageDao
import com.dculus.stayfocused.core.data.db.UsageDayEntity
import com.dculus.stayfocused.core.data.db.UsageHourEntity
import com.dculus.stayfocused.core.data.db.UsageTotalsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/** Room-backed store of complete past days. */
interface UsageCache {
    /** Replaces whatever is cached for `stats.date`. */
    suspend fun store(stats: DayUsageStats)

    /** The cached day, or null when it isn't cached. `firstAfterUnlock` isn't cached and reads as 0. */
    fun observeDay(date: LocalDate): Flow<DayUsageStats?>

    /** Totals of cached days in `[from, to]`, oldest first. */
    fun observeTotals(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<DayTotals>>

    suspend fun cachedDates(
        from: LocalDate,
        to: LocalDate,
    ): Set<LocalDate>

    /** Drops every cached day strictly before [date]. */
    suspend fun prune(before: LocalDate)
}

internal class RoomUsageCache
    @Inject
    constructor(
        private val dao: UsageDao,
    ) : UsageCache {
        override suspend fun store(stats: DayUsageStats) {
            val date = stats.date
            dao.replaceDay(
                totals =
                    UsageTotalsEntity(
                        date = date,
                        totalMs = stats.totalMillis,
                        opens = stats.totalOpens,
                        unlocks = stats.unlocks,
                    ),
                days = stats.apps.map { UsageDayEntity(date, it.pkg, it.millis, it.opens) },
                hours =
                    List(HOURS_PER_DAY) { hour ->
                        UsageHourEntity(
                            date = date,
                            hour = hour,
                            foregroundMs = stats.hourlyMillis.getOrElse(hour) { 0L },
                            unlocks = stats.hourlyUnlocks.getOrElse(hour) { 0 },
                        )
                    },
            )
        }

        override fun observeDay(date: LocalDate): Flow<DayUsageStats?> =
            combine(
                dao.observeTotals(date, date),
                dao.observeDays(date, date),
                dao.observeHours(date),
            ) { totals, days, hours ->
                val row = totals.firstOrNull() ?: return@combine null
                val hourlyMillis = LongArray(HOURS_PER_DAY)
                val hourlyUnlocks = IntArray(HOURS_PER_DAY)
                hours.filter { it.hour in 0 until HOURS_PER_DAY }.forEach {
                    hourlyMillis[it.hour] = it.foregroundMs
                    hourlyUnlocks[it.hour] = it.unlocks
                }
                DayUsageStats(
                    date = date,
                    totalMillis = row.totalMs,
                    apps =
                        days
                            .map { AppUsageStat(it.pkg, it.foregroundMs, it.opens, firstAfterUnlock = 0) }
                            .sortedByDescending { it.millis },
                    hourlyMillis = hourlyMillis.toList(),
                    unlocks = row.unlocks,
                    hourlyUnlocks = hourlyUnlocks.toList(),
                )
            }

        override fun observeTotals(
            from: LocalDate,
            to: LocalDate,
        ): Flow<List<DayTotals>> =
            dao.observeTotals(from, to).map { rows ->
                rows.map { DayTotals(it.date, it.totalMs, it.opens, it.unlocks) }
            }

        override suspend fun cachedDates(
            from: LocalDate,
            to: LocalDate,
        ): Set<LocalDate> =
            dao
                .observeTotals(from, to)
                .first()
                .map { it.date }
                .toSet()

        override suspend fun prune(before: LocalDate) = dao.deleteBefore(before)
    }
