package com.dculus.stayfocused.core.usage

import androidx.room.Room
import app.cash.turbine.test
import com.dculus.stayfocused.core.data.db.BlockEventEntity
import com.dculus.stayfocused.core.data.db.StayFocusedDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeDataSource(
    private val days: MutableMap<LocalDate, DayUsageStats> = mutableMapOf(),
) : UsageStatsDataSource {
    val queried = mutableListOf<LocalDate>()

    fun put(stats: DayUsageStats) {
        days[stats.date] = stats
    }

    override suspend fun dayUsage(date: LocalDate): DayUsageStats {
        queried += date
        return days[date] ?: DayUsageStats.empty(date)
    }
}

private class FakeAccess(
    var granted: Boolean = true,
) : UsageAccess {
    override fun isGranted() = granted

    override fun settingsIntent() = throw UnsupportedOperationException()
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DefaultUsageRepositoryTest {
    private var zone: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 3, 10)
    private val clock = Clock.fixed(Instant.parse("2026-03-10T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: StayFocusedDatabase
    private lateinit var dataSource: FakeDataSource
    private lateinit var access: FakeAccess
    private lateinit var repository: DefaultUsageRepository

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), StayFocusedDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dataSource = FakeDataSource()
        access = FakeAccess()
        repository =
            DefaultUsageRepository(dataSource, RoomUsageCache(db.usageDao()), access, db.blockEventDao(), clock)
                .apply { zoneProvider = { zone } }
    }

    @After
    fun tearDown() = db.close()

    private fun stats(
        date: LocalDate,
        mins: Int,
        unlocks: Int = 10,
    ): DayUsageStats {
        val hourly = List(HOURS_PER_DAY) { if (it == 9) mins * 60_000L else 0L }
        return DayUsageStats(
            date = date,
            totalMillis = mins * 60_000L,
            apps =
                listOf(
                    AppUsageStat("a", (mins - 1) * 60_000L, opens = 5, firstAfterUnlock = 2),
                    AppUsageStat("b", 60_000L, opens = 3, firstAfterUnlock = 0),
                ),
            hourlyMillis = hourly,
            unlocks = unlocks,
            hourlyUnlocks = List(HOURS_PER_DAY) { if (it == 9) unlocks else 0 },
        )
    }

    @Test
    fun `backfill caches the missing complete days and day() reads them back`() =
        runTest {
            dataSource.put(stats(today.minusDays(1), 120))
            dataSource.put(stats(today.minusDays(3), 60, unlocks = 4))

            repository.backfill()

            val yesterday = repository.day(today.minusDays(1)).first()
            assertEquals(120, yesterday?.totalMins)
            assertEquals(listOf("a", "b"), yesterday?.apps?.map { it.pkg })
            assertEquals(8, yesterday?.totalOpens)
            assertEquals(10, yesterday?.unlocks)
            assertEquals(120 * 60_000L, yesterday?.hourlyMillis?.get(9))
            assertEquals(HOURS_PER_DAY, yesterday?.hourlyUnlocks?.size)
            assertEquals(4, repository.day(today.minusDays(3)).first()?.unlocks)
            // Days with no data aren't cached; today is never cached.
            assertNull(repository.day(today.minusDays(2)).first())
            assertNull(repository.day(today).first())
            assertTrue(today !in dataSource.queried)
        }

    @Test
    fun `backfill only queries days that are not cached yet`() =
        runTest {
            dataSource.put(stats(today.minusDays(1), 120))
            repository.backfill()
            dataSource.queried.clear()

            repository.backfill()

            assertTrue(today.minusDays(1) !in dataSource.queried)
            assertTrue(today.minusDays(2) in dataSource.queried)
        }

    @Test
    fun `backfill does nothing without usage access`() =
        runTest {
            access.granted = false
            dataSource.put(stats(today.minusDays(1), 120))

            repository.backfill()

            assertTrue(dataSource.queried.isEmpty())
            assertNull(repository.day(today.minusDays(1)).first())
        }

    @Test
    fun `averages cover the previous days that have data, none for a first-day user`() =
        runTest {
            assertEquals(UsageAverages.NONE, repository.averages().first())

            dataSource.put(stats(today.minusDays(1), 60, unlocks = 10))
            dataSource.put(stats(today.minusDays(4), 180, unlocks = 20))
            repository.backfill()

            val averages = repository.averages().first()
            assertEquals(2, averages.daysCounted)
            assertEquals(120, averages.avgTotalMins)
            assertEquals(8, averages.avgOpens)
            assertEquals(15, averages.avgUnlocks)
        }

    @Test
    fun `averages ignore days older than the window`() =
        runTest {
            dataSource.put(stats(today.minusDays(7), 60))
            dataSource.put(stats(today.minusDays(8), 600))
            repository.backfill()

            val averages = repository.averages().first()
            assertEquals(1, averages.daysCounted)
            assertEquals(60, averages.avgTotalMins)
        }

    @Test
    fun `today is live and refreshes on request and every minute`() =
        runTest {
            dataSource.put(stats(today, 10))
            repository.today().test {
                assertEquals(10, awaitItem().totalMins)

                dataSource.put(stats(today, 20))
                repository.requestRefresh()
                assertEquals(20, awaitItem().totalMins)

                dataSource.put(stats(today, 30))
                advanceTimeBy(UsageRepository.REFRESH_INTERVAL_MILLIS + 1)
                assertEquals(30, awaitItem().totalMins)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `blockedToday counts only block events of the local day`() =
        runTest {
            val dao = db.blockEventDao()
            val midnight = today.atStartOfDay(ZoneOffset.UTC).toInstant()
            dao.insert(BlockEventEntity(pkg = "a", blockId = null, reason = "r", at = midnight.minusSeconds(1)))
            dao.insert(BlockEventEntity(pkg = "a", blockId = null, reason = "r", at = midnight))
            dao.insert(BlockEventEntity(pkg = "b", blockId = null, reason = "r", at = midnight.plusSeconds(3_600)))

            assertEquals(2, repository.blockedToday().first())
        }
}
