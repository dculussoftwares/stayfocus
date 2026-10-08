package com.dculus.stayfocused.feature.home

import app.cash.turbine.test
import com.dculus.stayfocused.core.testing.FakeBreakRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private class VirtualClock(
        private val scheduler: TestCoroutineScheduler,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = START.plusMillis(scheduler.currentTime)
    }

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun idleWithoutABreak() =
        runTest {
            val clock = VirtualClock(testScheduler)
            val vm = HomeViewModel(FakeBreakRepository(clock), clock)
            vm.breakUi.test { assertEquals(BreakUi.Idle, awaitItem()) }
        }

    @Test fun startedBreakCountsDownAndEndsByItself() =
        runTest {
            val clock = VirtualClock(testScheduler)
            val breaks = FakeBreakRepository(clock)
            val vm = HomeViewModel(breaks, clock)
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
            val vm = HomeViewModel(breaks, clock)
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
            val vm = HomeViewModel(breaks, clock)
            vm.breakUi.test {
                skipItems(1)
                assertIs<BreakUi.Active>(awaitItem())
                vm.endBreak()
                testScheduler.runCurrent()
                assertEquals(BreakUi.Idle, awaitItem())
            }
        }

    private companion object {
        val START: Instant = Instant.parse("2026-10-08T10:00:00Z")
    }
}
