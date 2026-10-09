package com.dculus.stayfocused.core.blocking.engine

import com.dculus.stayfocused.core.blocking.evaluator.AppUsageSnapshot
import com.dculus.stayfocused.core.usage.PackageUsageSource
import com.dculus.stayfocused.core.usage.UsageAccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Usage of today and of the current clock hour = the last UsageStats aggregate + what the engine measured since
 * that aggregate was taken. UsageStats is only re-read every [CACHE_MS] (it is a heavy query and the engine asks
 * often), and the foreground time of the live session since then comes from the engine, so the limit can be
 * exhausted at the exact moment while the app stays open. The aggregate covers everything up to its own timestamp,
 * the engine delta everything after it: no second is counted twice.
 *
 * Without usage access the engine's own measurements (since the process started) are used instead.
 */
@Singleton
class UsageStatsAppUsageProvider
    @Inject
    internal constructor(
        private val source: PackageUsageSource,
        private val access: UsageAccess,
        private val live: LiveForegroundSource,
        private val fallback: ForegroundTimingUsage,
    ) : AppUsageProvider {
        private class Aggregate(
            val pkgs: Set<String>,
            val dayStart: Instant,
            val hourStart: Instant,
            val takenAt: Instant,
            val today: Map<String, Long>,
            val hour: Map<String, Long>,
        )

        private val mutex = Mutex()
        private var cached: Aggregate? = null

        override suspend fun usage(
            pkgs: Set<String>,
            now: ZonedDateTime,
        ): Map<String, AppUsageSnapshot> {
            if (!access.isGranted()) return fallback.usage(pkgs, now)
            val at = now.toInstant()
            val agg = aggregate(pkgs, now)
            return pkgs.associateWith { pkg ->
                AppUsageSnapshot(
                    todayMs = merge(agg.today[pkg], pkg, agg.dayStart, agg.takenAt, at),
                    thisHourMs = merge(agg.hour[pkg], pkg, agg.hourStart, agg.takenAt, at),
                )
            }
        }

        private fun merge(
            aggregated: Long?,
            pkg: String,
            windowStart: Instant,
            takenAt: Instant,
            now: Instant,
        ): Long {
            val since = maxOf(takenAt, windowStart)
            val delta = if (now > since) live.foregroundMs(pkg, since, now) else 0L
            return (aggregated ?: 0L) + delta
        }

        private suspend fun aggregate(
            pkgs: Set<String>,
            now: ZonedDateTime,
        ): Aggregate =
            mutex.withLock {
                val at = now.toInstant()
                val dayStart = now.toLocalDate().atStartOfDay(now.zone).toInstant()
                val hourStart = now.truncatedTo(ChronoUnit.HOURS).toInstant()
                cached?.takeIf { fresh(it, pkgs, dayStart, hourStart, at) } ?: run {
                    Aggregate(
                        pkgs = pkgs,
                        dayStart = dayStart,
                        hourStart = hourStart,
                        takenAt = at,
                        today = source.foregroundMillis(dayStart, at),
                        hour = source.foregroundMillis(hourStart, at),
                    ).also { cached = it }
                }
            }

        private fun fresh(
            a: Aggregate,
            pkgs: Set<String>,
            dayStart: Instant,
            hourStart: Instant,
            now: Instant,
        ): Boolean =
            a.dayStart == dayStart &&
                a.hourStart == hourStart &&
                a.pkgs == pkgs &&
                !now.isBefore(a.takenAt) &&
                now.toEpochMilli() - a.takenAt.toEpochMilli() < CACHE_MS

        companion object {
            /** How long a UsageStats aggregate is reused before it is read again. */
            const val CACHE_MS = 20_000L
        }
    }
