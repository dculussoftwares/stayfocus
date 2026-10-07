package com.dculus.stayfocused.core.usage

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Turns a raw event stream into a [DayUsageStats]. Pure and deterministic.
 *
 * Events should include some lead-in before the day starts (see [LOOKBACK_MILLIS]) so an app that was already in
 * the foreground at midnight is known; time before the day is never counted.
 *
 * Rules:
 * - An app is in the foreground while at least one of its activities is resumed. Time is clipped to the day
 *   and split across local hours.
 * - Open sessions are closed at `SCREEN_NON_INTERACTIVE`, `DEVICE_SHUTDOWN` and at the end of the window.
 *   A missing pause therefore never runs past a screen-off.
 * - An open is counted when an app becomes foreground and differs from the previously foreground package
 *   (excluded packages such as the launcher count as "previous", so A -> home -> A is two opens). The
 *   previous package is forgotten at screen-off, so waking the phone into the same app is an open.
 * - Excluded packages (own apps, launchers, system UI) never appear in totals, opens or first-app counts.
 */
class UsageAggregator(
    private val zone: ZoneId,
    private val excluded: Set<String>,
    private val unlockSignal: UnlockSignal,
) {
    fun aggregate(
        date: LocalDate,
        events: List<RawUsageEvent>,
        nowMillis: Long,
    ): DayUsageStats {
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd =
            date
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        val windowEnd = minOf(dayEnd, nowMillis)
        if (windowEnd <= dayStart) return DayUsageStats.empty(date)

        val run = Run(dayStart, windowEnd)
        events.sortedBy { it.timeMillis }.forEach { event ->
            if (event.timeMillis < windowEnd) run.handle(event)
        }
        run.closeAll(windowEnd)
        return run.toStats(date)
    }

    private inner class Run(
        private val windowStart: Long,
        private val windowEnd: Long,
    ) {
        private val resumed = HashMap<String, MutableSet<String>>()
        private val openSince = HashMap<String, Long>()
        private val millis = HashMap<String, Long>()
        private val opens = HashMap<String, Int>()
        private val firstAfterUnlock = HashMap<String, Int>()
        private val hourlyMillis = LongArray(HOURS_PER_DAY)
        private val hourlyUnlocks = IntArray(HOURS_PER_DAY)
        private var unlocks = 0
        private var previousForeground: String? = null
        private var awaitingFirstApp = false

        fun handle(event: RawUsageEvent) {
            val inWindow = event.timeMillis >= windowStart
            when (event.type) {
                RawEventType.ACTIVITY_RESUMED -> {
                    resume(event, inWindow)
                }

                RawEventType.ACTIVITY_PAUSED -> {
                    pause(event)
                }

                RawEventType.SCREEN_NON_INTERACTIVE, RawEventType.DEVICE_SHUTDOWN -> {
                    closeAll(event.timeMillis)
                    previousForeground = null
                    awaitingFirstApp = false
                }

                RawEventType.KEYGUARD_HIDDEN -> {
                    if (unlockSignal == UnlockSignal.KEYGUARD_HIDDEN) unlock(event.timeMillis, inWindow)
                }

                RawEventType.SCREEN_INTERACTIVE -> {
                    if (unlockSignal == UnlockSignal.SCREEN_INTERACTIVE) unlock(event.timeMillis, inWindow)
                }
            }
        }

        private fun unlock(
            at: Long,
            inWindow: Boolean,
        ) {
            awaitingFirstApp = inWindow
            if (!inWindow) return
            unlocks++
            hourlyUnlocks[hourOf(at)]++
        }

        private fun resume(
            event: RawUsageEvent,
            inWindow: Boolean,
        ) {
            val pkg = event.pkg
            val activities = resumed.getOrPut(pkg) { mutableSetOf() }
            val becameForeground = activities.isEmpty()
            activities += event.className
            if (!becameForeground) return
            openSince[pkg] = event.timeMillis
            val isNewApp = pkg != previousForeground
            previousForeground = pkg
            if (pkg in excluded) return
            if (inWindow && isNewApp) opens.merge(pkg, 1, Int::plus)
            if (awaitingFirstApp) {
                awaitingFirstApp = false
                firstAfterUnlock.merge(pkg, 1, Int::plus)
            }
        }

        private fun pause(event: RawUsageEvent) {
            val activities = resumed[event.pkg] ?: return
            if (!activities.remove(event.className) || activities.isNotEmpty()) return
            resumed.remove(event.pkg)
            closeSession(event.pkg, event.timeMillis)
        }

        fun closeAll(at: Long) {
            resumed.clear()
            openSince.keys.toList().forEach { closeSession(it, at) }
        }

        private fun closeSession(
            pkg: String,
            end: Long,
        ) {
            val start = openSince.remove(pkg) ?: return
            if (pkg in excluded) return
            addSpan(pkg, maxOf(start, windowStart), minOf(end, windowEnd))
        }

        private fun addSpan(
            pkg: String,
            start: Long,
            end: Long,
        ) {
            var cursor = start
            while (cursor < end) {
                val next = minOf(end, nextHourBoundary(cursor))
                val span = next - cursor
                millis.merge(pkg, span, Long::plus)
                hourlyMillis[hourOf(cursor)] += span
                cursor = next
            }
        }

        private fun hourOf(at: Long): Int = Instant.ofEpochMilli(at).atZone(zone).hour

        /** Start of the next local hour after [at]; always strictly greater than [at]. */
        private fun nextHourBoundary(at: Long): Long {
            val localHour =
                Instant
                    .ofEpochMilli(at)
                    .atZone(zone)
                    .toLocalDateTime()
                    .withMinute(0)
                    .withSecond(0)
                    .withNano(0)

            // A local hour can be repeated or skipped at a zone transition. Try each
            // local hour boundary and retain only instants that are valid in this zone.
            for (hoursAhead in 0..48) {
                val candidate = localHour.plusHours(hoursAhead.toLong())
                zone.rules.getValidOffsets(candidate).forEach { offset ->
                    val boundary = candidate.atOffset(offset).toInstant().toEpochMilli()
                    if (boundary > at) return boundary
                }
            }
            return at + 1
        }

        fun toStats(date: LocalDate): DayUsageStats {
            val apps =
                millis.keys
                    .map { AppUsageStat(it, millis.getValue(it), opens[it] ?: 0, firstAfterUnlock[it] ?: 0) }
                    .sortedWith(compareByDescending<AppUsageStat> { it.millis }.thenBy { it.pkg })
            return DayUsageStats(
                date = date,
                totalMillis = apps.sumOf { it.millis },
                apps = apps,
                hourlyMillis = hourlyMillis.toList(),
                unlocks = unlocks,
                hourlyUnlocks = hourlyUnlocks.toList(),
            )
        }
    }

    companion object {
        /** Lead-in queried before the day starts, to learn which app was already in the foreground at midnight. */
        const val LOOKBACK_MILLIS: Long = 6 * 60 * 60 * 1000L
    }
}
