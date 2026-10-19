package com.dculus.stayfocused.core.usage

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class DayUsageStatsTest {
    private val date = LocalDate.of(2026, 3, 4)

    @Test
    fun hourlyMinutesAreRoundedWholeMinutesPerHour() {
        val hourlyMillis =
            List(24) { hour ->
                if (hour == 8) {
                    90_000L
                } else if (hour == 9) {
                    20_000L
                } else {
                    0L
                }
            }
        val stats = DayUsageStats.empty(date).copy(hourlyMillis = hourlyMillis)
        assertEquals(24, stats.hourlyMins.size)
        assertEquals(2, stats.hourlyMins[8])
        assertEquals(0, stats.hourlyMins[9])
    }

    @Test
    fun anHourNeverExceedsSixtyMinutes() {
        val stats = DayUsageStats.empty(date).copy(hourlyMillis = List(24) { 3 * 3_600_000L })
        assertEquals(List(24) { 60 }, stats.hourlyMins)
    }
}
