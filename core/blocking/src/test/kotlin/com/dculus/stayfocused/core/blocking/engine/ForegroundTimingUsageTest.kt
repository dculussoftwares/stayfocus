package com.dculus.stayfocused.core.blocking.engine

import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class ForegroundTimingUsageTest {
    private val usage = ForegroundTimingUsage()
    private val now = Instant.parse("2023-11-14T10:30:00Z").atZone(ZoneOffset.UTC)

    private fun at(time: String) = Instant.parse("2023-11-14T${time}Z")

    @Test fun clipsToTodayAndThisHour() =
        runTest {
            usage.record("a", at("09:50:00"), at("10:10:00"))
            usage.record("a", Instant.parse("2023-11-13T23:50:00Z"), at("00:10:00"))
            val snap = usage.usage(setOf("a", "b"), now).getValue("a")
            assertEquals(30 * 60_000L, snap.todayMs)
            assertEquals(10 * 60_000L, snap.thisHourMs)
            assertEquals(0L, usage.usage(setOf("b"), now).getValue("b").todayMs)
        }

    @Test fun adjacentIntervalsMerge() =
        runTest {
            usage.record("a", at("10:00:00"), at("10:00:30"))
            usage.record("a", at("10:00:30"), at("10:01:00"))
            assertEquals(60_000L, usage.usage(setOf("a"), now).getValue("a").thisHourMs)
        }

    @Test fun emptyIntervalsAreIgnored() =
        runTest {
            usage.record("a", at("10:00:00"), at("10:00:00"))
            assertEquals(0L, usage.usage(setOf("a"), now).getValue("a").todayMs)
        }
}
