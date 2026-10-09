package com.dculus.stayfocused.core.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Reads one day of usage from the system. Returns an empty day when usage access isn't granted. */
interface UsageStatsDataSource {
    suspend fun dayUsage(date: LocalDate): DayUsageStats
}

/** A time window [from, to) to total foreground time over. */
data class UsageWindow(
    val from: Instant,
    val to: Instant,
)

/** Foreground time per package over arbitrary windows; used by the blocking engine for limits. */
interface PackageUsageSource {
    /**
     * Per window, the foreground milliseconds of each package; the events are read once for all windows. Own apps,
     * launchers and system UI are never included. Null when usage access isn't granted (not the same as no usage).
     */
    suspend fun foregroundMillis(windows: List<UsageWindow>): List<Map<String, Long>>?
}

private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

@Singleton
class AndroidUsageStatsDataSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val usageAccess: UsageAccess,
    ) : UsageStatsDataSource,
        PackageUsageSource {
        private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

        override suspend fun dayUsage(date: LocalDate): DayUsageStats =
            withContext(ioDispatcher) {
                if (!usageAccess.isGranted()) return@withContext DayUsageStats.empty(date)
                val zone = ZoneId.systemDefault()
                val now = System.currentTimeMillis()
                val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val dayEnd =
                    date
                        .plusDays(1)
                        .atStartOfDay(zone)
                        .toInstant()
                        .toEpochMilli()
                val queryEnd = minOf(dayEnd, now)
                if (queryEnd <= dayStart) return@withContext DayUsageStats.empty(date)

                val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val events = readEvents(manager, dayStart - UsageAggregator.LOOKBACK_MILLIS, queryEnd)
                val signal =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        UnlockSignal.KEYGUARD_HIDDEN
                    } else {
                        UnlockSignal.SCREEN_INTERACTIVE
                    }
                UsageAggregator(zone, excludedPackages(), signal).aggregate(date, events, queryEnd)
            }

        override suspend fun foregroundMillis(windows: List<UsageWindow>): List<Map<String, Long>>? =
            withContext(ioDispatcher) {
                if (!usageAccess.isGranted()) return@withContext null
                val valid = windows.filter { it.to > it.from }
                if (valid.isEmpty()) return@withContext windows.map { emptyMap() }
                val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val earliest = valid.minOf { it.from.toEpochMilli() }
                val latest = valid.maxOf { it.to.toEpochMilli() }
                val events = readEvents(manager, earliest - UsageAggregator.LOOKBACK_MILLIS, latest)
                // The unlock signal is irrelevant for foreground time.
                val aggregator =
                    UsageAggregator(ZoneId.systemDefault(), excludedPackages(), UnlockSignal.SCREEN_INTERACTIVE)
                windows.map { aggregator.foregroundMillis(events, it.from.toEpochMilli(), it.to.toEpochMilli()) }
            }

        private fun readEvents(
            manager: UsageStatsManager,
            from: Long,
            to: Long,
        ): List<RawUsageEvent> {
            val result = mutableListOf<RawUsageEvent>()
            val usageEvents = manager.queryEvents(from, to) ?: return result
            val event = UsageEvents.Event()
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                val type = mapEventType(event.eventType, Build.VERSION.SDK_INT) ?: continue
                result += RawUsageEvent(event.packageName.orEmpty(), event.className.orEmpty(), type, event.timeStamp)
            }
            return result
        }

        private fun excludedPackages(): Set<String> {
            val pm = context.packageManager
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val launchers: List<ResolveInfo> =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(home, PackageManager.ResolveInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(home, 0)
                }
            return OwnPackages + SYSTEM_UI_PACKAGE + context.packageName + launchers.map { it.activityInfo.packageName }
        }
    }

/**
 * Maps a platform `UsageEvents.Event` type to a [RawEventType] for the given API level, or `null` when the
 * event doesn't matter. `MOVE_TO_FOREGROUND`/`MOVE_TO_BACKGROUND` (API 26-28) share their values with
 * `ACTIVITY_RESUMED`/`ACTIVITY_PAUSED` (API 29+), so one mapping serves both.
 */
internal fun mapEventType(
    eventType: Int,
    sdkInt: Int,
): RawEventType? =
    when (eventType) {
        UsageEvents.Event.SCREEN_INTERACTIVE -> {
            RawEventType.SCREEN_INTERACTIVE
        }

        UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
            RawEventType.SCREEN_NON_INTERACTIVE
        }

        UsageEvents.Event.DEVICE_SHUTDOWN -> {
            RawEventType.DEVICE_SHUTDOWN
        }

        UsageEvents.Event.KEYGUARD_HIDDEN -> {
            RawEventType.KEYGUARD_HIDDEN.takeIf { sdkInt >= Build.VERSION_CODES.P }
        }

        UsageEvents.Event.ACTIVITY_RESUMED -> {
            RawEventType.ACTIVITY_RESUMED
        }

        UsageEvents.Event.ACTIVITY_PAUSED -> {
            RawEventType.ACTIVITY_PAUSED
        }

        else -> {
            null
        }
    }
