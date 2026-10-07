package com.dculus.stayfocused.core.usage

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UsageAveragesTest {
    private val base = LocalDate.of(2026, 3, 10)

    private fun day(
        offset: Int,
        mins: Int,
        opens: Int,
        unlocks: Int,
    ) = DayTotals(base.plusDays(offset.toLong()), mins * 60_000L, opens, unlocks)

    @Test
    fun `first-day user has no data and zero averages`() {
        val averages = UsageAverages.of(emptyList())
        assertEquals(UsageAverages.NONE, averages)
        assertFalse(averages.hasData)
        assertEquals(0L, averages.avgHourlyMillis)
    }

    @Test
    fun `a single cached day is its own average`() {
        val averages = UsageAverages.of(listOf(day(0, 240, 80, 30)))
        assertTrue(averages.hasData)
        assertEquals(1, averages.daysCounted)
        assertEquals(240, averages.avgTotalMins)
        assertEquals(80, averages.avgOpens)
        assertEquals(30, averages.avgUnlocks)
    }

    @Test
    fun `missing days are not counted as zero`() {
        // Only 3 of the 7 days have data: the mean is over those 3.
        val averages = UsageAverages.of(listOf(day(0, 60, 10, 5), day(3, 120, 20, 10), day(5, 180, 30, 15)))
        assertEquals(3, averages.daysCounted)
        assertEquals(120, averages.avgTotalMins)
        assertEquals(20, averages.avgOpens)
        assertEquals(10, averages.avgUnlocks)
    }

    @Test
    fun `full week averages and rounds opens and unlocks`() {
        val days = (0 until 7).map { day(it, 100 + it * 10, 10 + it, 4) }
        val averages = UsageAverages.of(days)
        assertEquals(7, averages.daysCounted)
        assertEquals(130, averages.avgTotalMins)
        assertEquals(13, averages.avgOpens)
        assertEquals(4, averages.avgUnlocks)
        // 2.5 rounds up.
        assertEquals(3, UsageAverages.of(listOf(day(0, 1, 2, 0), day(1, 1, 3, 0))).avgOpens)
    }

    @Test
    fun `hourly line is the average total divided by 24`() {
        val averages = UsageAverages.of(listOf(day(0, 240, 0, 0), day(1, 480, 0, 0)))
        assertEquals(360 * 60_000L / 24, averages.avgHourlyMillis)
    }
}
