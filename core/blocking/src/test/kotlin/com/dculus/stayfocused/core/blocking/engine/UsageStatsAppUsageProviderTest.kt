package com.dculus.stayfocused.core.blocking.engine

import android.content.Intent
import com.dculus.stayfocused.core.usage.PackageUsageSource
import com.dculus.stayfocused.core.usage.UsageAccess
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.assertEquals

class UsageStatsAppUsageProviderTest {
    /** Answers [today] for the window that starts at midnight and [hour] for any other window. */
    private class FakeSource(
        var today: Map<String, Long> = emptyMap(),
        var hour: Map<String, Long> = emptyMap(),
    ) : PackageUsageSource {
        var calls = 0

        override suspend fun foregroundMillis(
            from: Instant,
            to: Instant,
        ): Map<String, Long> {
            calls++
            val z = from.atZone(ZoneOffset.UTC)
            return if (z.hour == 0 && z.minute == 0) today else hour
        }
    }

    private class FakeAccess(
        var granted: Boolean = true,
    ) : UsageAccess {
        override fun isGranted() = granted

        override fun settingsIntent(): Intent = error("unused")
    }

    private val timing = ForegroundTimingUsage()
    private val source = FakeSource()
    private val access = FakeAccess()
    private val provider = UsageStatsAppUsageProvider(source, access, timing, timing)

    private fun at(time: String) = Instant.parse("2023-11-14T${time}Z")

    private fun zoned(time: String): ZonedDateTime = at(time).atZone(ZoneOffset.UTC)

    @Test
    fun addsTheLiveDeltaSinceTheAggregateToTheAggregate() =
        runTest {
            source.today = mapOf("a" to 40 * MIN)
            source.hour = mapOf("a" to 5 * MIN)
            val first = provider.usage(setOf("a"), zoned("10:30:00")).getValue("a")
            assertEquals(40 * MIN, first.todayMs)
            assertEquals(5 * MIN, first.thisHourMs)

            // The app stays open: the engine measured 10:30:00-10:30:12 while UsageStats is not re-read.
            timing.record("a", at("10:30:00"), at("10:30:12"))
            val second = provider.usage(setOf("a"), zoned("10:30:12")).getValue("a")
            assertEquals(40 * MIN + 12_000, second.todayMs)
            assertEquals(5 * MIN + 12_000, second.thisHourMs)
            assertEquals(2, source.calls)
        }

    @Test
    fun doesNotCountEngineTimeBeforeTheAggregateTwice() =
        runTest {
            timing.record("a", at("10:20:00"), at("10:29:00"))
            source.today = mapOf("a" to 9 * MIN)
            source.hour = mapOf("a" to 9 * MIN)
            val u = provider.usage(setOf("a"), zoned("10:30:00")).getValue("a")
            assertEquals(9 * MIN, u.todayMs)
            assertEquals(9 * MIN, u.thisHourMs)
        }

    @Test
    fun rereadsAfterTheCacheExpires() =
        runTest {
            source.today = mapOf("a" to MIN)
            provider.usage(setOf("a"), zoned("10:30:00"))
            source.today = mapOf("a" to 2 * MIN)
            val u = provider.usage(setOf("a"), zoned("10:30:30")).getValue("a")
            assertEquals(2 * MIN, u.todayMs)
        }

    @Test
    fun resetsAtTheTopOfTheHourAndAtMidnight() =
        runTest {
            source.today = mapOf("a" to 30 * MIN)
            source.hour = mapOf("a" to 29 * MIN)
            provider.usage(setOf("a"), zoned("10:59:50"))
            source.hour = emptyMap()
            val nextHour = provider.usage(setOf("a"), zoned("11:00:05")).getValue("a")
            assertEquals(0L, nextHour.thisHourMs)
            assertEquals(30 * MIN, nextHour.todayMs)

            source.today = emptyMap()
            val nextDay = provider.usage(setOf("a"), ZonedDateTime.parse("2023-11-15T00:00:05Z")).getValue("a")
            assertEquals(0L, nextDay.todayMs)
        }

    @Test
    fun usesOnlyTheEnginesOwnTimeWithoutUsageAccess() =
        runTest {
            access.granted = false
            source.today = mapOf("a" to 99 * MIN)
            timing.record("a", at("10:00:00"), at("10:10:00"))
            val u = provider.usage(setOf("a"), zoned("10:30:00")).getValue("a")
            assertEquals(10 * MIN, u.todayMs)
            assertEquals(0, source.calls)
        }

    private companion object {
        const val MIN = 60_000L
    }
}
