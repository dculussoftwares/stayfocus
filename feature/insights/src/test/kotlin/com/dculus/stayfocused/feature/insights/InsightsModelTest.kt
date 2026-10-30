package com.dculus.stayfocused.feature.insights

import com.dculus.stayfocused.core.usage.AppUsageStat
import com.dculus.stayfocused.core.usage.DayUsageStats
import org.junit.Test
import java.time.LocalDate
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InsightsModelTest {
    private val min = 60_000L
    private val date = LocalDate.of(2026, 10, 6)

    private fun app(
        pkg: String,
        mins: Long,
        opens: Int,
        first: Int = 0,
    ) = AppUsageStat(pkg, mins * min, opens, first)

    private val apps =
        listOf(
            app("a", mins = 41, opens = 5, first = 1),
            app("b", mins = 33, opens = 20, first = 9),
            app("c", mins = 33, opens = 20, first = 9),
            app("d", mins = 0, opens = 0, first = 0),
        )

    @Test
    fun `ranks by screen time and drops zero apps`() {
        val ranked = rankApps(apps, InsightsMetric.ScreenTime) { it }
        assertEquals(listOf("a", "b", "c"), ranked.map { it.pkg })
        assertEquals(1f, ranked.first().fraction)
        assertEquals(33f / 41f, ranked[1].fraction)
    }

    @Test
    fun `brief sessions still rank as one minute`() {
        val brief = AppUsageStat("brief", 10_000L, opens = 1, firstAfterUnlock = 0)
        val ranked = rankApps(listOf(brief), InsightsMetric.ScreenTime) { it }
        assertEquals(listOf(1), ranked.map { it.value })
    }

    @Test
    fun `ranks opens with screen time then label as tie-breakers`() {
        val ranked = rankApps(apps, InsightsMetric.Opens) { it }
        assertEquals(listOf("b", "c", "a"), ranked.map { it.pkg })
    }

    @Test
    fun `nothing to rank gives an empty list`() {
        assertTrue(rankApps(listOf(app("a", 0, 0, 0)), InsightsMetric.Opens) { it }.isEmpty())
        assertTrue(rankApps(emptyList(), InsightsMetric.Opens) { it }.isEmpty())
    }

    @Test
    fun `day titles`() {
        assertEquals(DayTitle.Today, dayTitle(0, date))
        assertEquals(DayTitle.Yesterday, dayTitle(-1, date.minusDays(1)))
        assertEquals(DayTitle.Weekday("Sunday"), dayTitle(-2, LocalDate.of(2026, 10, 4), Locale.UK))
        assertEquals(DayTitle.Weekday("Monday"), dayTitle(-6, LocalDate.of(2026, 9, 28), Locale.UK))
    }

    @Test
    fun `date label is two digit day and upper case month`() {
        assertEquals("06 OCT", dayDateLabel(date, Locale.UK))
        assertEquals("01 JAN", dayDateLabel(LocalDate.of(2026, 1, 1), Locale.ENGLISH))
    }

    @Test
    fun `stepper bounds are today and minus six`() {
        assertFalse(canStepForward(0))
        assertTrue(canStepForward(-1))
        assertTrue(canStepBack(-5))
        assertFalse(canStepBack(-6))
    }

    private fun dayStats(
        mins: Long,
        hourly: List<Long> = List(24) { 0L },
        apps: List<AppUsageStat> = emptyList(),
    ) = DayUsageStats(date, mins * min, apps, hourly, unlocks = 3, hourlyUnlocks = List(24) { 0 })

    @Test
    fun `day uses hourly minutes, a floor of 20 on the scale and total over 24 as average`() {
        val hourly = List(24) { if (it == 10) 30 * min else 0L }
        val day = buildDay(dayStats(144, hourly, apps), InsightsMetric.ScreenTime, currentHour = 10) { it }
        assertEquals(InsightsRange.Day, day.range)
        assertEquals(144, day.heroMins)
        assertEquals(30, day.values[10])
        assertEquals(30, day.chartMax)
        assertEquals(6f, day.average)
        assertEquals(10, day.highlightIndex)
        assertEquals(3, day.apps.size)
        assertEquals(DAY_CHART_MIN_MAX, buildDay(dayStats(5), InsightsMetric.ScreenTime, null) { it }.chartMax)
        assertNull(buildDay(dayStats(5), InsightsMetric.ScreenTime, null) { it }.highlightIndex)
    }

    @Test
    fun `day keeps only the top six apps`() {
        val many = (1..9).map { app("p$it", mins = it.toLong(), opens = it) }
        val day = buildDay(dayStats(45, apps = many), InsightsMetric.ScreenTime, null) { it }
        assertEquals(listOf("p9", "p8", "p7", "p6", "p5", "p4"), day.apps.map { it.pkg })
    }

    @Test
    fun `week averages seven days, highlights today and sums apps`() {
        val days =
            List(7) { i ->
                if (i == 1) {
                    null
                } else {
                    dayStats((60 * (i + 1)).toLong(), apps = listOf(app("a", mins = 10, opens = 2)))
                }
            }
        val week = buildWeek(days, InsightsMetric.ScreenTime, listOf("M")) { it }
        assertEquals(listOf(60, 0, 180, 240, 300, 360, 420), week.values)
        assertEquals(420, week.chartMax)
        assertEquals(1560 / 7f, week.average)
        assertEquals(223, week.heroMins)
        assertEquals(6, week.highlightIndex)
        val top = week.apps.single()
        assertEquals(60, top.value)
        val opens = buildWeek(days, InsightsMetric.Opens, emptyList()) { it }
        assertEquals(12, opens.apps.single().value)
    }

    @Test
    fun `week axis starts at the right weekday and ends today`() {
        // 2026-10-08 is a Thursday.
        assertEquals(
            listOf("F", "S", "S", "M", "T", "W", "T"),
            weekAxisLabels(LocalDate.of(2026, 10, 8), Locale.ENGLISH),
        )
    }

    @Test
    fun `hero parts pad minutes`() {
        assertEquals("2" to "33", dayHeroParts(153))
        assertEquals("0" to "05", dayHeroParts(5))
    }

    @Test
    fun `empty days are detected`() {
        assertTrue(null.hasNoData())
        assertTrue(DayUsageStats.empty(date).hasNoData())
        assertFalse(DayUsageStats.empty(date).copy(unlocks = 1).hasNoData())
        assertFalse(DayUsageStats.empty(date).copy(totalMillis = 1_000).hasNoData())
    }

    private fun ranked(
        totalMillis: Long,
        lastSessionMillis: Long,
        value: Int = 1,
    ) = RankedApp(1, "p", "P", value, 1f, totalMillis, 0, lastSessionMillis)

    @Test
    fun `short sessions count as one minute`() {
        assertEquals(1, sessionMinutes(1_000))
        assertEquals(1, sessionMinutes(0))
        assertEquals(8, sessionMinutes(8 * 60_000L))
    }

    @Test
    fun `recent share is measured in milliseconds not rounded minutes`() {
        val share = recentShare(ranked(50_000, 1_000), InsightsMetric.ScreenTime, InsightsRange.Day)
        assertEquals(0.02f, share, 0.001f)
        assertEquals(1f, recentShare(ranked(1_000, 5_000), InsightsMetric.ScreenTime, InsightsRange.Day))
    }

    @Test
    fun `recent share is zero when unknown or off the day screen time list`() {
        assertEquals(0f, recentShare(ranked(60_000, 0), InsightsMetric.ScreenTime, InsightsRange.Day))
        assertEquals(0f, recentShare(ranked(0, 5_000), InsightsMetric.ScreenTime, InsightsRange.Day))
        assertEquals(0f, recentShare(ranked(60_000, 5_000), InsightsMetric.Opens, InsightsRange.Day))
        assertEquals(0f, recentShare(ranked(60_000, 5_000), InsightsMetric.ScreenTime, InsightsRange.Week))
    }

    @Test
    fun `aggregating days keeps the latest session`() {
        val older = AppUsageStat("p", 60_000, 1, 0, lastUsedMillis = 1_000, lastSessionMillis = 10_000)
        val newer = AppUsageStat("p", 120_000, 2, 0, lastUsedMillis = 9_000, lastSessionMillis = 30_000)
        val app = aggregateApps(listOf(newer, older).map { DayUsageStats.empty(date).copy(apps = listOf(it)) }).single()
        assertEquals(180_000L, app.millis)
        assertEquals(9_000L, app.lastUsedMillis)
        assertEquals(30_000L, app.lastSessionMillis)
    }
}
