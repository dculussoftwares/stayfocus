package com.dculus.stayfocused.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LedLevelsTest {
    private fun levels(
        h: List<Int>,
        rows: Int,
        cur: Int? = null,
    ) = ledLevels(h, rows, cur).map { it.level }

    @Test
    fun allZeros() {
        assertEquals(List(24) { 0 }, levels(List(24) { 0 }, 5, 10))
    }

    @Test
    fun singleSpikeFillsColumnAndTinyValuesStayVisible() {
        val h =
            List(24) {
                if (it == 7) {
                    30
                } else if (it == 8) {
                    1
                } else {
                    0
                }
            }
        val l = levels(h, 10)
        assertEquals(10, l[7])
        assertEquals(1, l[8])
        assertEquals(0, l[9])
    }

    @Test
    fun futureHoursAreZeroAndCurrentIsFlagged() {
        val h = List(24) { 10 }
        val cols = ledLevels(h, 5, 10)
        assertTrue(cols.take(11).all { it.level == 5 })
        assertTrue(cols.drop(11).all { it.level == 0 })
        assertEquals(listOf(10), cols.withIndex().filter { it.value.isCurrent }.map { it.index })
        assertTrue(ledLevels(h, 5, null).none { it.isCurrent })
    }

    @Test
    fun roundsHalfUpAndKeepsSmallValuesLit() {
        // max 22, 5 rows: 11 -> 2.5 -> 3, 22 -> 5, 2 -> 0.45 -> min 1, 9 -> 2.05 -> 2
        assertEquals(listOf(3, 5, 1, 2), levels(listOf(11, 22, 2, 9), 5))
    }

    @Test
    fun prototypeSampleData() {
        val hours = listOf(0, 0, 0, 0, 0, 0, 2, 6, 14, 9, 5, 8, 12, 4, 3, 7, 10, 6, 9, 18, 22, 15, 8, 2)
        val l = levels(hours, 10, 10)
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 1, 3, 6, 4, 2), l.take(11))
        assertEquals(List(13) { 0 }, l.drop(11))
    }
}
