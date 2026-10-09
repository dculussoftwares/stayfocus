package com.dculus.stayfocused.feature.home

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.dculus.stayfocused.core.data.repository.BlockRepository
import com.dculus.stayfocused.core.data.repository.BreakRepository
import com.dculus.stayfocused.core.model.AppInfo
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.LedColumn
import com.dculus.stayfocused.core.testing.FakeAccountProfileRepository
import com.dculus.stayfocused.core.testing.FakeBlockRepository
import com.dculus.stayfocused.core.testing.FakeBreakRepository
import com.dculus.stayfocused.core.testing.FakeLinkedDevicesRepository
import com.dculus.stayfocused.core.testing.FakeSettingsRepository
import com.dculus.stayfocused.core.usage.DayUsageStats
import com.dculus.stayfocused.core.usage.UsageAverages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private class VirtualClock(
        private val scheduler: TestCoroutineScheduler,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = START.plusMillis(scheduler.currentTime)
    }

    private class Env(
        val usage: FakeUsage = FakeUsage(),
        val access: FakeUsageAccess = FakeUsageAccess(),
        val blocks: FakeBlockRepository = FakeBlockRepository(),
        val installed: FakeInstalledApps = FakeInstalledApps(),
        val devices: FakeLinkedDevicesRepository = FakeLinkedDevicesRepository(),
        val profile: FakeAccountProfileRepository = FakeAccountProfileRepository(),
        val settings: FakeSettingsRepository = FakeSettingsRepository(),
    )

    /** Lets the pending flow work run, then returns the newest state the turbine received. */
    private fun ReceiveTurbine<HomeUiState>.latest(scheduler: TestCoroutineScheduler): HomeUiState {
        scheduler.runCurrent()
        return expectMostRecentItem()
    }

    private fun viewModel(
        clock: Clock,
        env: Env = Env(),
        breaks: BreakRepository = FakeBreakRepository(clock),
        blocks: BlockRepository = env.blocks,
    ) = HomeViewModel(
        breaks,
        clock,
        env.usage,
        env.access,
        blocks,
        env.installed,
        env.devices,
        env.profile,
        env.settings,
    )

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun idleWithoutABreak() =
        runTest {
            val clock = VirtualClock(testScheduler)
            val vm = viewModel(clock)
            vm.breakUi.test { assertEquals(BreakUi.Idle, awaitItem()) }
        }

    @Test fun startedBreakCountsDownAndEndsByItself() =
        runTest {
            val clock = VirtualClock(testScheduler)
            val breaks = FakeBreakRepository(clock)
            val vm = viewModel(clock, breaks = breaks)
            vm.breakUi.test {
                assertEquals(BreakUi.Idle, awaitItem())
                vm.startBreak(5)
                testScheduler.runCurrent()
                assertEquals(BreakUi.Active(300_000, 300_000), awaitItem())
                testScheduler.advanceTimeBy(1_000)
                testScheduler.runCurrent()
                assertEquals(BreakUi.Active(299_000, 300_000), awaitItem())
                testScheduler.advanceTimeBy(299_000)
                testScheduler.runCurrent()
                cancelAndConsumeRemainingEvents().let { events ->
                    val last = events.filterIsInstance<app.cash.turbine.Event.Item<BreakUi>>().last().value
                    assertEquals(BreakUi.Idle, last)
                }
            }
        }

    @Test fun resumesFromTheStoredEndTimeAfterTheViewModelIsRecreated() =
        runTest {
            val clock = VirtualClock(testScheduler)
            val breaks = FakeBreakRepository(clock)
            breaks.start(10)
            testScheduler.advanceTimeBy(4 * 60_000L)
            // A new ViewModel (process death) reads the same stored break.
            val vm = viewModel(clock, breaks = breaks)
            vm.breakUi.test {
                skipItems(1)
                val active = awaitItem()
                assertIs<BreakUi.Active>(active)
                assertEquals(6 * 60_000L, active.remainingMs)
                assertEquals("6:00", active.countdown)
            }
        }

    @Test fun endBreakReturnsToIdle() =
        runTest {
            val clock = VirtualClock(testScheduler)
            val breaks = FakeBreakRepository(clock)
            breaks.start(30)
            val vm = viewModel(clock, breaks = breaks)
            vm.breakUi.test {
                skipItems(1)
                assertIs<BreakUi.Active>(awaitItem())
                vm.endBreak()
                testScheduler.runCurrent()
                assertEquals(BreakUi.Idle, awaitItem())
            }
        }

    @Test fun startReportsSuccessOnlyOnceTheBreakIsStored() =
        runTest {
            val clock = VirtualClock(testScheduler)
            val vm = viewModel(clock)
            vm.events.test {
                vm.startBreak(15)
                assertEquals(HomeEvent.BreakStarted(15), awaitItem())
            }
        }

    @Test fun failedWritesAreReportedInsteadOfCrashing() =
        runTest {
            val clock = VirtualClock(testScheduler)
            val failing =
                object : BreakRepository by FakeBreakRepository(clock) {
                    override suspend fun start(mins: Int) = error("disk full")

                    override suspend fun end() = error("disk full")
                }
            val vm = viewModel(clock, breaks = failing)
            vm.events.test {
                vm.startBreak(15)
                assertEquals(HomeEvent.BreakFailed, awaitItem())
                vm.endBreak()
                assertEquals(HomeEvent.BreakFailed, awaitItem())
            }
        }

    @Test fun headerShowsTheDateAndTheAccountsFirstName() =
        runTest {
            val clock = VirtualClock(testScheduler)
            val env = Env(profile = FakeAccountProfileRepository("Natheesh"))
            val vm = viewModel(clock, env)
            vm.state.test {
                val header = latest(testScheduler).header
                assertEquals("THU 08 OCT · 10:00", header.dateLabel.uppercase())
                assertEquals(Greeting.MORNING, header.greeting)
                assertEquals("Natheesh", header.firstName)
                assertEquals("N", header.initial)
            }
        }

    @Test fun headerHasNoNameWithoutAnAccount() =
        runTest {
            val vm = viewModel(VirtualClock(testScheduler))
            vm.state.test {
                val header = latest(testScheduler).header
                assertNull(header.firstName)
                assertNull(header.initial)
            }
        }

    @Test fun headerClockMovesOnEveryMinute() =
        runTest {
            val vm = viewModel(VirtualClock(testScheduler))
            vm.state.test {
                assertEquals("THU 08 OCT · 10:00", latest(testScheduler).header.dateLabel.uppercase())
                testScheduler.advanceTimeBy(60_000)
                testScheduler.runCurrent()
                assertEquals("THU 08 OCT · 10:01", latest(testScheduler).header.dateLabel.uppercase())
            }
        }

    @Test fun gaugeShowsTodayAgainstTheAverage() =
        runTest {
            val env = Env(usage = FakeUsage(typicalDay(), averages(139), blocked = 12))
            val vm = viewModel(VirtualClock(testScheduler), env)
            vm.state.test {
                val gauge = assertIs<GaugeUi.Ready>(latest(testScheduler).gauge)
                assertEquals(147, gauge.totalMins)
                assertEquals(139, gauge.avgMins)
                assertEquals(8, gauge.deltaMins)
                assertEquals(63, gauge.launches)
                assertEquals(48, gauge.unlocks)
                assertEquals(12, gauge.blocked)
                assertEquals(24, gauge.levels.size)
                // 10:00 is the current hour; later hours stay dark even though the sample has data there.
                assertEquals(LedColumn(level = 1, isCurrent = true), gauge.levels[10])
                assertTrue(gauge.levels.drop(11).all { it.level == 0 })
            }
        }

    @Test fun gaugeHasNoAverageBeforeAnyCompleteDay() =
        runTest {
            val env = Env(usage = FakeUsage(typicalDay(), UsageAverages.NONE))
            val vm = viewModel(VirtualClock(testScheduler), env)
            vm.state.test {
                val gauge = assertIs<GaugeUi.Ready>(latest(testScheduler).gauge)
                assertNull(gauge.avgMins)
                assertNull(gauge.deltaMins)
            }
        }

    @Test fun gaugeAsksForUsageAccessWhenItIsOff() =
        runTest {
            val env = Env(usage = FakeUsage(typicalDay(), averages(139)), access = FakeUsageAccess(granted = false))
            val vm = viewModel(VirtualClock(testScheduler), env)
            vm.state.test { assertEquals(GaugeUi.NoAccess, latest(testScheduler).gauge) }
        }

    @Test fun grantingAccessInSettingsShowsTheGaugeOnResume() =
        runTest {
            val env = Env(usage = FakeUsage(typicalDay(), averages(139)), access = FakeUsageAccess(granted = false))
            val vm = viewModel(VirtualClock(testScheduler), env)
            vm.state.test {
                assertEquals(GaugeUi.NoAccess, latest(testScheduler).gauge)
                env.access.granted = true
                vm.refreshUsageAccess()
                testScheduler.runCurrent()
                assertIs<GaugeUi.Ready>(latest(testScheduler).gauge)
                assertEquals(1, env.usage.refreshes)
                assertEquals(1, env.usage.backfills)
            }
        }

    @Test fun failedBackfillIsRetriedOnTheNextResume() =
        runTest {
            val env = Env(access = FakeUsageAccess(granted = false))
            env.usage.failBackfill = true
            val vm = viewModel(VirtualClock(testScheduler), env)
            vm.state.test {
                latest(testScheduler)
                env.access.granted = true
                vm.refreshUsageAccess()
                testScheduler.runCurrent()
                env.usage.failBackfill = false
                vm.refreshUsageAccess()
                testScheduler.runCurrent()
                vm.refreshUsageAccess()
                testScheduler.runCurrent()
                assertEquals(2, env.usage.backfills)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun shortSessionsStillLightTheirHour() =
        runTest {
            val short = DayUsageStats.empty(TestDay).copy(hourlyMillis = List(24) { if (it == 9) 30_000L else 0L })
            val vm = viewModel(VirtualClock(testScheduler), Env(usage = FakeUsage(short)))
            vm.state.test {
                val gauge = assertIs<GaugeUi.Ready>(latest(testScheduler).gauge)
                assertEquals(HOME_LED_ROWS, gauge.levels[9].level)
            }
        }

    @Test fun ordinaryResumesDoNotBackfillAgain() =
        runTest {
            val env = Env()
            val vm = viewModel(VirtualClock(testScheduler), env)
            vm.state.test {
                latest(testScheduler)
                vm.refreshUsageAccess()
                vm.refreshUsageAccess()
                testScheduler.runCurrent()
                assertEquals(0, env.usage.backfills)
            }
        }

    @Test fun usageSettingsIntentComesFromTheAccessChecker() {
        val env = Env()
        val vm = viewModel(VirtualClock(TestCoroutineScheduler()), env)
        assertSame(env.access.intent, vm.usageSettingsIntent())
    }

    @Test fun listsThisPhonesBlocksWithAppLabels() =
        runTest {
            val env = Env(installed = FakeInstalledApps(listOf(AppInfo(pkg("instagram"), "Instagram"))))
            env.blocks.upsert(cycleBlock())
            env.blocks.upsert(scheduleBlock())
            env.blocks.upsert(cycleBlock(id = "child", target = BlockTarget.Device("d1")))
            val vm = viewModel(VirtualClock(testScheduler), env)
            vm.state.test {
                val rows = latest(testScheduler).blocks
                assertEquals(listOf("b1", "b3"), rows.map { it.block.id })
                // Instagram is installed; YouTube is not and falls back to the prototype name.
                assertEquals(listOf("Instagram", "Youtube"), rows.first().appLabels)
            }
        }

    @Test fun togglingABlockStoresIt() =
        runTest {
            val env = Env()
            env.blocks.upsert(cycleBlock())
            val vm = viewModel(VirtualClock(testScheduler), env)
            vm.setBlockEnabled("b1", false)
            testScheduler.runCurrent()
            assertEquals(false, env.blocks.get("b1")?.enabled)
        }

    @Test fun failedBlockToggleIsReported() =
        runTest {
            val clock = VirtualClock(testScheduler)
            val failing =
                object : BlockRepository by FakeBlockRepository() {
                    override suspend fun setEnabled(
                        id: String,
                        enabled: Boolean,
                    ) = error("disk full")
                }
            val vm = viewModel(clock, blocks = failing)
            vm.events.test {
                vm.setBlockEnabled("b1", true)
                assertEquals(HomeEvent.BlockToggleFailed, awaitItem())
            }
        }

    @Test fun aiDescribeFollowsTheAiSetting() =
        runTest {
            val env = Env()
            val vm = viewModel(VirtualClock(testScheduler), env)
            vm.state.test {
                assertEquals(false, latest(testScheduler).aiAvailable)
                env.settings.setAiEnabled(true)
                assertEquals(true, latest(testScheduler).aiAvailable)
            }
        }

    @Test fun linkedPhonesAreListed() =
        runTest {
            val env = Env(devices = FakeLinkedDevicesRepository(listOf(device(alerts = 1))))
            val vm = viewModel(VirtualClock(testScheduler), env)
            vm.state.test { assertEquals(listOf("d1"), latest(testScheduler).devices.map { it.id }) }
        }

    private companion object {
        val START: Instant = Instant.parse("2026-10-08T10:00:00Z")
    }
}
