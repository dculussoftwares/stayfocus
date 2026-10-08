package com.dculus.stayfocused.core.blocking.engine

import com.dculus.stayfocused.core.blocking.evaluator.AppUsageSnapshot
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Temporary [AppUsageProvider]: only knows what the engine itself measured since the process started.
 * M2-06 replaces or augments it with UsageStats.
 */
@Singleton
class ForegroundTimingUsage
    @Inject
    constructor() :
    AppUsageProvider,
        ForegroundTimeRecorder {
        private class Interval(
            val pkg: String,
            val from: Instant,
            var to: Instant,
        )

        private val lock = Any()
        private val intervals = ArrayList<Interval>()

        override fun record(
            pkg: String,
            from: Instant,
            to: Instant,
        ) {
            if (!to.isAfter(from)) return
            synchronized(lock) {
                val last = intervals.lastOrNull()
                if (last != null && last.pkg == pkg && last.to == from) {
                    last.to = to
                } else {
                    intervals += Interval(pkg, from, to)
                }
            }
        }

        override suspend fun usage(
            pkgs: Set<String>,
            now: ZonedDateTime,
        ): Map<String, AppUsageSnapshot> {
            val dayStart = now.toLocalDate().atStartOfDay(now.zone).toInstant()
            val hourStart = now.truncatedTo(ChronoUnit.HOURS).toInstant()
            val end = now.toInstant()
            synchronized(lock) {
                intervals.removeAll { it.to <= dayStart }
                return pkgs.associateWith { pkg ->
                    val own = intervals.filter { it.pkg == pkg }
                    AppUsageSnapshot(
                        todayMs = own.sumOf { overlapMs(it, dayStart, end) },
                        thisHourMs = own.sumOf { overlapMs(it, hourStart, end) },
                    )
                }
            }
        }

        private fun overlapMs(
            i: Interval,
            from: Instant,
            to: Instant,
        ): Long {
            val start = maxOf(i.from, from)
            val stop = minOf(i.to, to)
            return if (stop > start) stop.toEpochMilli() - start.toEpochMilli() else 0L
        }
    }
