package com.dculus.stayfocused.core.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class UsageAggregatorTest {
    private val utc: ZoneId = ZoneOffset.UTC
    private val date: LocalDate = LocalDate.of(2026, 3, 10)
    private val dayStart = date.atStartOfDay(utc).toInstant().toEpochMilli()
    private val endOfDay = dayStart + DAY
    private val ig = "com.instagram.android"
    private val yt = "com.google.android.youtube"
    private val launcher = "com.launcher"
    private val own = "com.dculus.stayfocused"
    private val excluded = setOf(launcher, own, "com.android.systemui")

    private fun at(
        hour: Int,
        minute: Int = 0,
        second: Int = 0,
    ): Long = dayStart + hour * HOUR + minute * MIN + second * SEC

    private fun resumed(
        pkg: String,
        time: Long,
        cls: String = "Main",
    ) = RawUsageEvent(pkg, cls, RawEventType.ACTIVITY_RESUMED, time)

    private fun paused(
        pkg: String,
        time: Long,
        cls: String = "Main",
    ) = RawUsageEvent(pkg, cls, RawEventType.ACTIVITY_PAUSED, time)

    private fun system(
        type: RawEventType,
        time: Long,
    ) = RawUsageEvent("android", "", type, time)

    private fun aggregate(
        events: List<RawUsageEvent>,
        now: Long = endOfDay,
        zone: ZoneId = utc,
        signal: UnlockSignal = UnlockSignal.KEYGUARD_HIDDEN,
        day: LocalDate = date,
    ) = UsageAggregator(zone, excluded, signal).aggregate(day, events, now)

    private fun DayUsageStats.millisOf(pkg: String) = apps.firstOrNull { it.pkg == pkg }?.millis ?: 0L

    private fun DayUsageStats.opensOf(pkg: String) = apps.firstOrNull { it.pkg == pkg }?.opens ?: 0

    @Test
    fun `empty stream gives an empty day`() {
        val stats = aggregate(emptyList())
        assertEquals(0L, stats.totalMillis)
        assertTrue(stats.apps.isEmpty())
        assertEquals(HOURS_PER_DAY, stats.hourlyMillis.size)
        assertEquals(HOURS_PER_DAY, stats.hourlyUnlocks.size)
        assertEquals(0, stats.unlocks)
    }

    @Test
    fun `single session is summed`() {
        val stats = aggregate(listOf(resumed(ig, at(9)), paused(ig, at(9, 30))))
        assertEquals(30 * MIN, stats.millisOf(ig))
        assertEquals(30, stats.totalMins)
        assertEquals(1, stats.opensOf(ig))
    }

    @Test
    fun `two sessions of one app add up and count two opens when another app sits between`() {
        val stats =
            aggregate(
                listOf(
                    resumed(ig, at(9)),
                    paused(ig, at(9, 10)),
                    resumed(yt, at(9, 10)),
                    paused(yt, at(9, 20)),
                    resumed(ig, at(9, 20)),
                    paused(ig, at(9, 25)),
                ),
            )
        assertEquals(15 * MIN, stats.millisOf(ig))
        assertEquals(10 * MIN, stats.millisOf(yt))
        assertEquals(2, stats.opensOf(ig))
        assertEquals(1, stats.opensOf(yt))
    }

    @Test
    fun `apps are sorted busiest first`() {
        val stats =
            aggregate(
                listOf(
                    resumed(ig, at(9)),
                    paused(ig, at(9, 5)),
                    resumed(yt, at(10)),
                    paused(yt, at(10, 20)),
                ),
            )
        assertEquals(listOf(yt, ig), stats.apps.map { it.pkg })
    }

    @Test
    fun `overlapping activities of one app are one session`() {
        val stats =
            aggregate(
                listOf(
                    resumed(ig, at(9), "A"),
                    resumed(ig, at(9, 5), "B"),
                    paused(ig, at(9, 5), "A"),
                    paused(ig, at(9, 10), "B"),
                ),
            )
        assertEquals(10 * MIN, stats.millisOf(ig))
        assertEquals(1, stats.opensOf(ig))
    }

    @Test
    fun `activity switch inside an app does not count as a new open`() {
        val stats =
            aggregate(
                listOf(
                    resumed(ig, at(9), "Feed"),
                    paused(ig, at(9, 5), "Feed"),
                    resumed(ig, at(9, 5), "Profile"),
                    paused(ig, at(9, 8), "Profile"),
                ),
            )
        assertEquals(8 * MIN, stats.millisOf(ig))
        assertEquals(1, stats.opensOf(ig))
    }

    @Test
    fun `a pause for an activity that was never resumed is ignored`() {
        val stats = aggregate(listOf(paused(ig, at(9)), resumed(ig, at(10)), paused(ig, at(10, 5))))
        assertEquals(5 * MIN, stats.millisOf(ig))
    }

    @Test
    fun `missing pause closes at the end of the window`() {
        val stats = aggregate(listOf(resumed(ig, at(9))), now = at(9, 45))
        assertEquals(45 * MIN, stats.millisOf(ig))
    }

    @Test
    fun `missing pause closes at screen off`() {
        val stats =
            aggregate(
                listOf(
                    resumed(ig, at(9)),
                    system(RawEventType.SCREEN_NON_INTERACTIVE, at(9, 12)),
                    system(RawEventType.SCREEN_INTERACTIVE, at(11)),
                ),
            )
        assertEquals(12 * MIN, stats.millisOf(ig))
    }

    @Test
    fun `device shutdown closes open sessions`() {
        val stats =
            aggregate(listOf(resumed(ig, at(9)), system(RawEventType.DEVICE_SHUTDOWN, at(9, 7))))
        assertEquals(7 * MIN, stats.millisOf(ig))
    }

    @Test
    fun `a late pause after screen off does not extend the session`() {
        val stats =
            aggregate(
                listOf(
                    resumed(ig, at(9)),
                    system(RawEventType.SCREEN_NON_INTERACTIVE, at(9, 5)),
                    paused(ig, at(9, 5, 1)),
                ),
            )
        assertEquals(5 * MIN, stats.millisOf(ig))
    }

    @Test
    fun `session crossing midnight into the day is clipped at the day start`() {
        val stats =
            aggregate(
                listOf(
                    resumed(ig, dayStart - 10 * MIN),
                    paused(ig, dayStart + 20 * MIN),
                ),
            )
        assertEquals(20 * MIN, stats.millisOf(ig))
        assertEquals(20 * MIN, stats.hourlyMillis[0])
        assertEquals(0, stats.opensOf(ig))
    }

    @Test
    fun `session crossing midnight out of the day is clipped at the day end`() {
        val stats =
            aggregate(
                listOf(
                    resumed(ig, endOfDay - 15 * MIN),
                    paused(ig, endOfDay + 30 * MIN),
                ),
            )
        assertEquals(15 * MIN, stats.millisOf(ig))
        assertEquals(15 * MIN, stats.hourlyMillis[23])
    }

    @Test
    fun `events after the window end are ignored`() {
        val stats =
            aggregate(
                listOf(resumed(ig, at(9)), paused(ig, at(9, 10)), resumed(ig, at(12)), paused(ig, at(12, 10))),
                now = at(10),
            )
        assertEquals(10 * MIN, stats.millisOf(ig))
    }

    @Test
    fun `a day entirely in the future is empty`() {
        val stats = aggregate(listOf(resumed(ig, at(9))), now = dayStart - HOUR)
        assertEquals(0L, stats.totalMillis)
    }

    @Test
    fun `session spanning hours is split across hourly buckets`() {
        val stats = aggregate(listOf(resumed(ig, at(9, 40)), paused(ig, at(11, 10))))
        assertEquals(20 * MIN, stats.hourlyMillis[9])
        assertEquals(HOUR, stats.hourlyMillis[10])
        assertEquals(10 * MIN, stats.hourlyMillis[11])
        assertEquals(90 * MIN, stats.hourlyMillis.sum())
    }

    @Test
    fun `excluded packages never count`() {
        val stats =
            aggregate(
                listOf(
                    resumed(launcher, at(9)),
                    paused(launcher, at(9, 30)),
                    resumed(own, at(9, 30)),
                    paused(own, at(9, 40)),
                    resumed(ig, at(9, 40)),
                    paused(ig, at(9, 50)),
                ),
            )
        assertEquals(listOf(ig), stats.apps.map { it.pkg })
        assertEquals(10 * MIN, stats.totalMillis)
    }

    @Test
    fun `app to launcher to the same app is two opens`() {
        val stats =
            aggregate(
                listOf(
                    resumed(ig, at(9)),
                    paused(ig, at(9, 5)),
                    resumed(launcher, at(9, 5)),
                    paused(launcher, at(9, 6)),
                    resumed(ig, at(9, 6)),
                    paused(ig, at(9, 10)),
                ),
            )
        assertEquals(2, stats.opensOf(ig))
    }

    @Test
    fun `waking the phone into the same app is a new open`() {
        val stats =
            aggregate(
                listOf(
                    resumed(ig, at(9)),
                    system(RawEventType.SCREEN_NON_INTERACTIVE, at(9, 5)),
                    system(RawEventType.SCREEN_INTERACTIVE, at(10)),
                    resumed(ig, at(10)),
                    paused(ig, at(10, 5)),
                ),
            )
        assertEquals(2, stats.opensOf(ig))
        assertEquals(10 * MIN, stats.millisOf(ig))
    }

    @Test
    fun `unlocks are counted by keyguard hidden and bucketed by hour`() {
        val stats =
            aggregate(
                listOf(
                    system(RawEventType.KEYGUARD_HIDDEN, at(7, 30)),
                    system(RawEventType.KEYGUARD_HIDDEN, at(7, 50)),
                    system(RawEventType.KEYGUARD_HIDDEN, at(13)),
                    system(RawEventType.SCREEN_INTERACTIVE, at(14)),
                ),
            )
        assertEquals(3, stats.unlocks)
        assertEquals(2, stats.hourlyUnlocks[7])
        assertEquals(1, stats.hourlyUnlocks[13])
        assertEquals(0, stats.hourlyUnlocks[14])
    }

    @Test
    fun `screen interactive is the unlock signal on API 26 and 27`() {
        val stats =
            aggregate(
                listOf(
                    system(RawEventType.SCREEN_INTERACTIVE, at(7)),
                    system(RawEventType.KEYGUARD_HIDDEN, at(7, 1)),
                    system(RawEventType.SCREEN_INTERACTIVE, at(8)),
                ),
                signal = UnlockSignal.SCREEN_INTERACTIVE,
            )
        assertEquals(2, stats.unlocks)
    }

    @Test
    fun `an unlock before the day is not counted`() {
        val stats = aggregate(listOf(system(RawEventType.KEYGUARD_HIDDEN, dayStart - MIN)))
        assertEquals(0, stats.unlocks)
    }

    @Test
    fun `first app after unlock skips the launcher`() {
        val stats =
            aggregate(
                listOf(
                    system(RawEventType.KEYGUARD_HIDDEN, at(7)),
                    resumed(launcher, at(7)),
                    paused(launcher, at(7, 1)),
                    resumed(ig, at(7, 1)),
                    paused(ig, at(7, 5)),
                    resumed(yt, at(7, 5)),
                    paused(yt, at(7, 10)),
                ),
            )
        assertEquals(1, stats.apps.first { it.pkg == ig }.firstAfterUnlock)
        assertEquals(0, stats.apps.first { it.pkg == yt }.firstAfterUnlock)
    }

    @Test
    fun `first app after unlock is not carried over a screen off`() {
        val stats =
            aggregate(
                listOf(
                    system(RawEventType.KEYGUARD_HIDDEN, at(7)),
                    system(RawEventType.SCREEN_NON_INTERACTIVE, at(7, 1)),
                    resumed(ig, at(8)),
                    paused(ig, at(8, 5)),
                ),
            )
        assertEquals(0, stats.apps.first { it.pkg == ig }.firstAfterUnlock)
    }

    @Test
    fun `each unlock credits its own first app`() {
        val stats =
            aggregate(
                listOf(
                    system(RawEventType.KEYGUARD_HIDDEN, at(7)),
                    resumed(ig, at(7)),
                    paused(ig, at(7, 5)),
                    system(RawEventType.SCREEN_NON_INTERACTIVE, at(7, 5)),
                    system(RawEventType.KEYGUARD_HIDDEN, at(8)),
                    resumed(ig, at(8)),
                    paused(ig, at(8, 5)),
                    system(RawEventType.KEYGUARD_HIDDEN, at(9)),
                    resumed(yt, at(9)),
                    paused(yt, at(9, 5)),
                ),
            )
        assertEquals(2, stats.apps.first { it.pkg == ig }.firstAfterUnlock)
        assertEquals(1, stats.apps.first { it.pkg == yt }.firstAfterUnlock)
    }

    @Test
    fun `unsorted input is ordered by time`() {
        val stats = aggregate(listOf(paused(ig, at(9, 10)), resumed(ig, at(9))))
        assertEquals(10 * MIN, stats.millisOf(ig))
    }

    @Test
    fun `toDayUsage carries the date and rounded minutes`() {
        val stats = aggregate(listOf(resumed(ig, at(9)), paused(ig, at(9, 1, 40))))
        val day = stats.toDayUsage()
        assertEquals(date, day.date)
        assertEquals(2, day.totalMins)
    }

    @Test
    fun `half hour offset zones bucket by local hour`() {
        val kolkata = ZoneId.of("Asia/Kolkata")
        val start = date.atStartOfDay(kolkata).toInstant().toEpochMilli()
        val stats =
            aggregate(
                listOf(resumed(ig, start + 9 * HOUR + 50 * MIN), paused(ig, start + 10 * HOUR + 10 * MIN)),
                now = start + DAY,
                zone = kolkata,
            )
        assertEquals(10 * MIN, stats.hourlyMillis[9])
        assertEquals(10 * MIN, stats.hourlyMillis[10])
    }

    @Test
    fun `spring forward day has 23 hours of buckets without losing time`() {
        val ny = ZoneId.of("America/New_York")
        val dstDay = LocalDate.of(2026, 3, 8)
        val start = dstDay.atStartOfDay(ny).toInstant().toEpochMilli()
        val stats =
            aggregate(
                listOf(resumed(ig, start), paused(ig, start + 4 * HOUR)),
                now = start + DAY,
                zone = ny,
                day = dstDay,
            )
        assertEquals(4 * HOUR, stats.totalMillis)
        assertEquals(4 * HOUR, stats.hourlyMillis.sum())
    }

    @Test
    fun `event types map across API levels`() {
        assertEquals(RawEventType.ACTIVITY_RESUMED, mapEventType(1, 26))
        assertEquals(RawEventType.ACTIVITY_PAUSED, mapEventType(2, 26))
        assertEquals(RawEventType.ACTIVITY_RESUMED, mapEventType(1, 34))
        assertEquals(RawEventType.ACTIVITY_PAUSED, mapEventType(23, 34))
        assertEquals(RawEventType.SCREEN_INTERACTIVE, mapEventType(15, 26))
        assertEquals(RawEventType.SCREEN_NON_INTERACTIVE, mapEventType(16, 26))
        assertEquals(RawEventType.KEYGUARD_HIDDEN, mapEventType(18, 28))
        assertEquals(null, mapEventType(18, 27))
        assertEquals(RawEventType.DEVICE_SHUTDOWN, mapEventType(26, 26))
        assertEquals(null, mapEventType(5, 34))
    }

    private companion object {
        const val SEC = 1_000L
        const val MIN = 60 * SEC
        const val HOUR = 60 * MIN
        const val DAY = 24 * HOUR
    }
}
