package com.dculus.stayfocused.core.usage

import com.dculus.stayfocused.core.data.db.BlockEventDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Live "today" usage plus the Room-cached previous days and their averages. */
interface UsageRepository {
    /**
     * Live usage of today: emits on collection, again on every [requestRefresh] and every
     * [REFRESH_INTERVAL_MILLIS] while collected. Follows the local date across midnight.
     */
    fun today(): Flow<DayUsageStats>

    /** Asks every collector of [today] to re-read now. */
    fun requestRefresh()

    /** A previous complete day from the cache; null while it isn't cached (also for today, use [today]). */
    fun day(date: LocalDate): Flow<DayUsageStats?>

    /** Mean of the previous [UsageAverages.AVERAGE_WINDOW_DAYS] complete days that have cached data. */
    fun averages(): Flow<UsageAverages>

    /** Blocks that fired today (local day), live. */
    fun blockedToday(): Flow<Int>

    /** Caches every missing complete day of the window from the system. No-op without usage access. */
    suspend fun backfill()

    companion object {
        const val REFRESH_INTERVAL_MILLIS = 60_000L
    }
}

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultUsageRepository
    @Inject
    internal constructor(
        private val dataSource: UsageStatsDataSource,
        private val cache: UsageCache,
        private val usageAccess: UsageAccess,
        private val blockEvents: BlockEventDao,
        private val clock: Clock,
    ) : UsageRepository {
        /**
         * Resolved on every use so a device time-zone change is followed (as the data source does). Overridable in
         * tests; the injected [Clock] is UTC, so the local zone is resolved here.
         */
        internal var zoneProvider: () -> ZoneId = { ZoneId.systemDefault() }
        private val zone: ZoneId get() = zoneProvider()

        private val refreshRequests =
            MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        private fun currentDate(): LocalDate = clock.instant().atZone(zone).toLocalDate()

        /** Emits now and then every minute. */
        private fun ticks(): Flow<Unit> =
            flow {
                while (true) {
                    emit(Unit)
                    delay(UsageRepository.REFRESH_INTERVAL_MILLIS)
                }
            }

        private fun dates(): Flow<LocalDate> = ticks().map { currentDate() }.distinctUntilChanged()

        override fun today(): Flow<DayUsageStats> =
            merge(ticks(), refreshRequests).map { dataSource.dayUsage(currentDate()) }

        override fun requestRefresh() {
            refreshRequests.tryEmit(Unit)
        }

        override fun day(date: LocalDate): Flow<DayUsageStats?> = cache.observeDay(date)

        override fun averages(): Flow<UsageAverages> =
            dates().flatMapLatest { today ->
                cache
                    .observeTotals(today.minusDays(WINDOW), today.minusDays(1))
                    .map(UsageAverages::of)
            }

        override fun blockedToday(): Flow<Int> =
            dates().flatMapLatest { today ->
                blockEvents.observeCountBetween(
                    today.atStartOfDay(zone).toInstant(),
                    today.plusDays(1).atStartOfDay(zone).toInstant(),
                )
            }

        override suspend fun backfill() {
            if (!usageAccess.isGranted()) return
            val today = currentDate()
            val from = today.minusDays(WINDOW)
            val to = today.minusDays(1)
            val cached = cache.cachedDates(from, to)
            generateSequence(from) { it.plusDays(1) }
                .takeWhile { !it.isAfter(to) }
                .filter { it !in cached }
                .forEach { date ->
                    val stats = dataSource.dayUsage(date)
                    // Days the system no longer remembers come back empty: not "data", so not cached.
                    if (stats.totalMillis > 0 || stats.unlocks > 0) cache.store(stats)
                }
            cache.prune(from)
        }

        private companion object {
            const val WINDOW = UsageAverages.AVERAGE_WINDOW_DAYS.toLong()
        }
    }
