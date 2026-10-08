package com.dculus.stayfocused.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WelcomeTicksTest {
    @Test
    fun hasSixtyTicksWithAMajorEveryFifth() {
        assertEquals(60, welcomeTicks.size)
        assertEquals(12, welcomeTicks.count { it.major })
        assertTrue(welcomeTicks.filterIndexed { i, _ -> i % 5 == 0 }.all { it.major })
    }

    @Test
    fun litRangeIsTwoFortyToThreeSixtySixDegreesPlusTheWrap() {
        // Angles 240..354 (i = 40..59) and 0, 6 (i = 0, 1).
        val lit = welcomeTicks.withIndex().filter { it.value.lit }.map { it.index }
        assertEquals((0..1).toList() + (40..59).toList(), lit)
    }

    @Test
    fun firstTickPointsStraightUp() {
        val t = welcomeTicks.first()
        assertEquals(120f, t.x1, 0.01f)
        assertEquals(40f, t.y1, 0.01f) // major: inner radius 80
        assertEquals(28f, t.y2, 0.01f) // outer radius 92
    }
}
