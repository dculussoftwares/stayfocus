package com.dculus.stayfocused.feature.insights

import app.cash.turbine.test
import com.dculus.stayfocused.core.model.AppInfo
import com.dculus.stayfocused.core.usage.AppUsageStat
import com.dculus.stayfocused.core.usage.DayUsageStats
import com.dculus.stayfocused.core.usage.InstalledAppsRepository
import com.dculus.stayfocused.core.usage.UsageAverages
import com.dculus.stayfocused.core.usage.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModelTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-08T10:30:00Z"), ZoneId.of("UTC"))
    private val today: LocalDate = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate()

    private class FakeUsage(
        val live: MutableStateFlow<DayUsageStats>,
        val cached: MutableMap<LocalDate, DayUsageStats>,
    ) : UsageRepository {
        override fun today(): Flow<DayUsageStats> = live

        override fun requestRefresh() = Unit

        override fun day(date: LocalDate): Flow<DayUsageStats?> = flowOf(cached[date])

        override fun averages(): Flow<UsageAverages> = flowOf(UsageAverages.NONE)

        override fun blockedToday(): Flow<Int> = flowOf(4)

        var backfills = 0

        override suspend fun backfill() {
            backfills++
        }
    }

    private val installed =
        object : InstalledAppsRepository {
            override fun observeLaunchableApps(): Flow<List<AppInfo>> = flowOf(listOf(AppInfo("com.a", "Alpha")))
        }

    private fun stats(
        date: LocalDate,
        mins: Long,
    ) = DayUsageStats(
        date = date,
        totalMillis = mins * 60_000,
        apps = listOf(AppUsageStat("com.a", mins * 60_000, 3, 1)),
        hourlyMillis = List(24) { 0L },
        unlocks = 2,
        hourlyUnlocks = List(24) { 0 },
    )

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `steps through the week and shows the empty state for uncached days`() =
        runTest {
            val usage =
                FakeUsage(
                    MutableStateFlow(stats(today, 30)),
                    mutableMapOf(today.minusDays(1) to stats(today.minusDays(1), 90)),
                )
            val vm = InsightsViewModel(usage, installed, clock)
            vm.state.test {
                var s = awaitItem()
                while (s.loading || s.day == null) s = awaitItem()
                assertEquals(0, s.dayOffset)
                assertEquals(30, s.day!!.heroMins)
                assertEquals(
                    "Alpha",
                    s.day!!
                        .apps
                        .single()
                        .label,
                )
                assertEquals(false, s.canStepForward)

                vm.previousDay()
                s = awaitItem()
                while (s.dayOffset != -1 || s.day == null) s = awaitItem()
                assertEquals(90, s.day!!.heroMins)
                assertEquals(today.minusDays(1), s.date)

                vm.previousDay()
                s = awaitItem()
                while (s.dayOffset != -2) s = awaitItem()
                assertNull(s.day)

                repeat(10) { vm.previousDay() }
                testScheduler.runCurrent()
                val oldest = expectMostRecentItem()
                assertEquals(OLDEST_DAY_OFFSET, oldest.dayOffset)
                assertEquals(false, oldest.canStepBack)

                vm.selectMetric(InsightsMetric.Opens)
                assertNotNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `next day never goes past today`() =
        runTest {
            val usage = FakeUsage(MutableStateFlow(stats(today, 5)), mutableMapOf())
            val vm = InsightsViewModel(usage, installed, clock)
            vm.nextDay()
            testScheduler.runCurrent()
            assertEquals(1, usage.backfills)
            vm.state.map { it.dayOffset }.test {
                assertEquals(0, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `week sums seven days ending today and tiles show today`() =
        runTest {
            val cached = (1..6).associate { today.minusDays(it.toLong()) to stats(today.minusDays(it.toLong()), 60) }
            val usage = FakeUsage(MutableStateFlow(stats(today, 30)), cached.toMutableMap())
            val vm = InsightsViewModel(usage, installed, clock)
            vm.state.test {
                var s = awaitItem()
                vm.selectRange(InsightsRange.Week)
                while (s.range != InsightsRange.Week || s.day == null) s = awaitItem()
                val week = checkNotNull(s.day)
                assertEquals(listOf(60, 60, 60, 60, 60, 60, 30), week.values)
                assertEquals(6, week.highlightIndex)
                assertEquals(56, week.heroMins)
                assertEquals(390, week.apps.single().value)
                assertEquals(2, s.unlocksToday)
                assertEquals(4, s.blockedToday)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
