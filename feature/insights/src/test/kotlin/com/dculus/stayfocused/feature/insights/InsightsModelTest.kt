package com.dculus.stayfocused.feature.insights

import com.dculus.stayfocused.core.usage.AppUsageStat
import com.dculus.stayfocused.core.usage.DayUsageStats
import com.dculus.stayfocused.core.usage.UsageAverages
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
        assertEquals(listOf("01", "02", "03"), ranked.map { it.rankLabel })
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
    fun `unlocks rank by first app opened after unlock`() {
        val ranked = rankApps(apps, InsightsMetric.Unlocks) { it }
        assertEquals(listOf("b", "c", "a"), ranked.map { it.pkg })
        assertEquals(listOf(9, 9, 1), ranked.map { it.value })
    }

    @Test
    fun `nothing to rank gives an empty list`() {
        assertTrue(rankApps(listOf(app("a", 5, 1, 0)), InsightsMetric.Unlocks) { it }.isEmpty())
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

    @Test
    fun `builds totals averages and chart per metric`() {
        val hourly = List(24) { if (it == 10) 60 * min else 0L }
        val stats =
            DayUsageStats(
                date = date,
                totalMillis = 60 * min,
                apps = apps,
                hourlyMillis = hourly,
                unlocks = 12,
                hourlyUnlocks = List(24) { if (it == 8) 4 else 0 },
            )
        val averages = UsageAverages(daysCounted = 3, avgTotalMillis = 48 * min, avgOpens = 40, avgUnlocks = 24)

        val time = buildInsightsDay(stats, averages, InsightsMetric.ScreenTime, currentHour = null) { it }
        assertEquals(60, time.total)
        assertEquals(48, time.average)
        assertEquals(LED_ROWS, time.levels[10].level)
        assertEquals(0, time.levels[9].level)
        assertEquals((48 * 60f / 24) / 3600f, time.avgFraction!!, 1e-6f)

        val opens = buildInsightsDay(stats, averages, InsightsMetric.Opens, currentHour = null) { it }
        assertEquals(45, opens.total)
        assertEquals(40, opens.average)

        val unlocks = buildInsightsDay(stats, averages, InsightsMetric.Unlocks, currentHour = null) { it }
        assertEquals(12, unlocks.total)
        assertEquals(24, unlocks.average)
        assertEquals(LED_ROWS, unlocks.levels[8].level)
        assertEquals(1f / 4f, unlocks.avgFraction!!, 1e-6f)
    }

    @Test
    fun `no history gives no average and no average line`() {
        val stats = DayUsageStats.empty(date).copy(totalMillis = 5 * min, hourlyMillis = List(24) { 5 * min / 24 })
        val day = buildInsightsDay(stats, UsageAverages.NONE, InsightsMetric.ScreenTime, currentHour = 3) { it }
        assertNull(day.average)
        assertNull(day.avgFraction)
        assertTrue(day.levels[3].isCurrent)
    }

    @Test
    fun `empty days are detected`() {
        assertTrue(null.hasNoData())
        assertTrue(DayUsageStats.empty(date).hasNoData())
        assertFalse(DayUsageStats.empty(date).copy(unlocks = 1).hasNoData())
        assertFalse(DayUsageStats.empty(date).copy(totalMillis = 1_000).hasNoData())
    }
}
