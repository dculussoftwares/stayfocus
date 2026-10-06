package com.dculus.stayfocused.core.model

import java.time.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals

class FormattersTest {
    @Test
    fun formatMinutes() {
        listOf(0 to "0m", 41 to "41m", 59 to "59m", 60 to "1h 0m", 61 to "1h 1m", 90 to "1h 30m", 147 to "2h 27m", 480 to "8h 0m")
            .forEach { (m, e) -> assertEquals(e, formatMinutes(m), "formatMinutes($m)") }
        assertEquals("1h 0m", formatMinutes(59.6))
    }

    @Test
    fun mmss() {
        listOf(0L to "0:00", -5L to "0:00", 1L to "0:01", 59_000L to "0:59", 60_000L to "1:00", 61_000L to "1:01", 90_500L to "1:31", 480 * 60_000L to "480:00")
            .forEach { (ms, e) -> assertEquals(e, mmss(ms), "mmss($ms)") }
    }

    @Test
    fun hms() {
        listOf(0L to "00:00:00", -1L to "00:00:00", 59_999L to "00:00:59", 60_000L to "00:01:00", 3_661_000L to "01:01:01", 90 * 60_000L to "01:30:00", 480 * 60_000L to "08:00:00")
            .forEach { (ms, e) -> assertEquals(e, hms(ms), "hms($ms)") }
    }

    @Test
    fun dialLabel() {
        listOf(0 to "0m", 15 to "15m", 59 to "59m", 60 to "1h", 61 to "1h1", 90 to "1h30", 120 to "2h", 480 to "8h")
            .forEach { (m, e) -> assertEquals(e, dialLabel(m), "dialLabel($m)") }
    }

    @Test
    fun durationLabel() {
        listOf(0 to "0 min", 45 to "45 min", 59 to "59 min", 60 to "1 h", 61 to "1 h 1 min", 90 to "1 h 30 min", 480 to "8 h")
            .forEach { (m, e) -> assertEquals(e, durationLabel(m), "durationLabel($m)") }
    }

    @Test
    fun daysSummary() {
        listOf(
            DaysOfWeek.ALL to "every day",
            DaysOfWeek.WEEKDAYS to "on weekdays",
            DaysOfWeek.WEEKENDS to "on Sat, Sun",
            DaysOfWeek.NONE to "no days",
            DaysOfWeek.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY) to "on Mon, Wed",
            DaysOfWeek.of(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
            ) to "on Mon, Tue, Wed, Thu, Fri, Sat",
        ).forEach { (d, e) -> assertEquals(e, daysSummary(d)) }
    }
}
