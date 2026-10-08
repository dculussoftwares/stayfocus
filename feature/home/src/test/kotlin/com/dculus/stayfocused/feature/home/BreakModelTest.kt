package com.dculus.stayfocused.feature.home

import org.junit.Test
import kotlin.test.assertEquals

class BreakModelTest {
    @Test fun mmssRoundsSecondsUp() {
        assertEquals("0:00", mmss(0))
        assertEquals("0:00", mmss(-5))
        assertEquals("0:01", mmss(1))
        assertEquals("0:01", mmss(1_000))
        assertEquals("0:02", mmss(1_001))
        assertEquals("5:00", mmss(300_000))
        assertEquals("4:59", mmss(299_000))
        assertEquals("120:00", mmss(120 * 60_000L))
    }

    @Test fun elapsedSegmentsFollowThePrototype() {
        val total = 30 * 60_000L
        assertEquals(0, elapsedSegments(total, total))
        assertEquals(1, elapsedSegments(total - 1, total))
        assertEquals(15, elapsedSegments(total / 2, total))
        assertEquals(30, elapsedSegments(0, total))
        assertEquals(30, elapsedSegments(-1, total))
        assertEquals(30, elapsedSegments(10, 0))
    }

    @Test fun nextTickLandsOnTheSecondBoundary() {
        assertEquals(500, millisToNextTick(2_500))
        assertEquals(1_000, millisToNextTick(2_000))
        assertEquals(1, millisToNextTick(1))
        assertEquals(1_000, millisToNextTick(1_000))
    }

    @Test fun activeStateExposesCountdownAndSegments() {
        val state = BreakUi.Active(remainingMs = 4 * 60_000L + 1_000, totalMs = 5 * 60_000L)
        assertEquals("4:01", state.countdown)
        assertEquals(6, state.elapsedSegments)
    }
}
