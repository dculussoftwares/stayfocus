package com.dculus.stayfocused.feature.home

import com.dculus.stayfocused.core.model.GoalNote
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

    @Test fun dateLineMatchesThePrototype() {
        assertEquals("Tuesday, 6 October", headerFor(at(10, 42), "Sam", Locale.ENGLISH).dateLabel)
        assertEquals(
            "Sunday, 18 October",
            headerFor(ZonedDateTime.of(2026, 10, 18, 9, 0, 0, 0, ZoneOffset.UTC), null, Locale.ENGLISH).dateLabel,
        )
    }

    @Test fun blankNamesCountAsNoName() {
        assertNull(headerFor(at(9), "  ", Locale.ENGLISH).firstName)
        assertNull(headerFor(at(9), null, Locale.ENGLISH).initial)
    }

    @Test fun initialIsTheUppercasedFirstLetter() {
        assertEquals("S", headerFor(at(9), " sam ", Locale.ENGLISH).initial)
    }

    @Test fun deltaIsPositiveWhenAboveTheAverage() {
        val gauge = readyGauge(totalMins = 147, avgMins = 139)
        assertEquals(8, gauge.deltaMins)
        assertEquals(-5, gauge.copy(totalMins = 134).deltaMins)
        assertNull(gauge.copy(avgMins = null).deltaMins)
    }

    @Test fun goalNoteComparesTodayWithTheGoal() {
        assertEquals(GoalNote.Under(27), readyGauge(totalMins = 153, goalMins = 180).goalNote)
        assertEquals(GoalNote.Over(10), readyGauge(totalMins = 190, goalMins = 180).goalNote)
    }

    @Test fun heroNumeralsAreHoursAndPaddedMinutes() {
        assertEquals("2:33", clockMins(153))
        assertEquals("0:05", clockMins(5))
        assertEquals("10:00", clockMins(600))
    }

    @Test fun breakCountdownPadsMinutesAndRoundsSecondsUp() {
        assertEquals("24:51", paddedMmss(24 * 60_000L + 51_000L))
        assertEquals("06:00", paddedMmss(6 * 60_000L))
        assertEquals("00:01", paddedMmss(1L))
        assertEquals("00:00", paddedMmss(-5L))
    }

    @Test fun tiltedAppsAreTheAppsOfTheFirstActiveBlocks() {
        val state =
            homeState(blocks = blockRows(cycleBlock(), limitBlock(), scheduleBlock(enabled = true)))
        assertEquals(listOf(pkg("instagram"), pkg("youtube")), state.tiltedApps)
        assertEquals(3, state.activeBlockCount)
        val paused = homeState(blocks = blockRows(cycleBlock(enabled = false), limitBlock()))
        assertEquals(listOf(pkg("reddit"), pkg("x")), paused.tiltedApps)
        assertEquals(1, paused.activeBlockCount)
        assertEquals(emptyList(), homeState().tiltedApps)
    }

    @Test fun bellPointsAtTheFirstPhoneNeedingAttention() {
        val quiet = device(id = "d1")
        val alerting = device(id = "d2", alerts = 1)
        assertNull(homeState(devices = listOf(quiet)).attentionDevice)
        assertEquals("d2", homeState(devices = listOf(quiet, alerting)).attentionDevice?.id)
    }
}
