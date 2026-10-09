package com.dculus.stayfocused.feature.home

import com.dculus.stayfocused.core.model.LedColumn
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeModelTest {
    private fun at(
        hour: Int,
        minute: Int = 0,
    ) = ZonedDateTime.of(2026, 10, 6, hour, minute, 0, 0, ZoneOffset.UTC)

    @Test fun greetingFollowsTheHourOfDay() {
        assertEquals(Greeting.MORNING, Greeting.forHour(0))
        assertEquals(Greeting.MORNING, Greeting.forHour(11))
        assertEquals(Greeting.AFTERNOON, Greeting.forHour(12))
        assertEquals(Greeting.AFTERNOON, Greeting.forHour(17))
        assertEquals(Greeting.EVENING, Greeting.forHour(18))
        assertEquals(Greeting.EVENING, Greeting.forHour(23))
    }

    @Test fun dateLineMatchesThePrototype() {
        assertEquals("TUE 06 OCT · 10:42", headerFor(at(10, 42), "Sam", Locale.ENGLISH).dateLabel)
    }

    @Test fun blankNamesCountAsNoName() {
        assertNull(headerFor(at(9), "  ", Locale.ENGLISH).firstName)
        assertNull(headerFor(at(9), null, Locale.ENGLISH).initial)
    }

    @Test fun initialIsTheUppercasedFirstLetter() {
        assertEquals("S", headerFor(at(9), " sam ", Locale.ENGLISH).initial)
    }

    @Test fun deltaIsPositiveWhenAboveTheAverage() {
        val gauge = GaugeUi.Ready(147, 139, List(24) { LedColumn(0) }, 63, 48, 12)
        assertEquals(8, gauge.deltaMins)
        assertEquals(-5, gauge.copy(totalMins = 134).deltaMins)
        assertNull(gauge.copy(avgMins = null).deltaMins)
    }
}
