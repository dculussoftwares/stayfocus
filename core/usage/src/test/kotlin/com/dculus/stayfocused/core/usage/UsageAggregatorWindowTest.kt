package com.dculus.stayfocused.core.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class UsageAggregatorWindowTest {
    private val aggregator = UsageAggregator(ZoneOffset.UTC, setOf("com.launcher"), UnlockSignal.KEYGUARD_HIDDEN)
    private val chrome = "com.android.chrome"

    private fun resumed(t: Long) = RawUsageEvent(chrome, "Main", RawEventType.ACTIVITY_RESUMED, t)

    private fun paused(t: Long) = RawUsageEvent(chrome, "Main", RawEventType.ACTIVITY_PAUSED, t)

    @Test
    fun clipsASessionToTheWindow() {
        val events = listOf(resumed(0), paused(100 * MIN))
        val result = aggregator.foregroundMillis(events, 30 * MIN, 60 * MIN)
        assertEquals(30 * MIN, result.getValue(chrome))
    }

    @Test
    fun closesAnOpenSessionAtTheWindowEnd() {
        val result = aggregator.foregroundMillis(listOf(resumed(10 * MIN)), 0, 25 * MIN)
        assertEquals(15 * MIN, result.getValue(chrome))
    }

    @Test
    fun ignoresExcludedPackagesAndEmptyWindows() {
        val launcher = RawUsageEvent("com.launcher", "Main", RawEventType.ACTIVITY_RESUMED, 0)
        assertTrue(aggregator.foregroundMillis(listOf(launcher), 0, MIN).isEmpty())
        assertTrue(aggregator.foregroundMillis(listOf(resumed(0)), MIN, MIN).isEmpty())
    }

    private companion object {
        const val MIN = 60_000L
    }
}
