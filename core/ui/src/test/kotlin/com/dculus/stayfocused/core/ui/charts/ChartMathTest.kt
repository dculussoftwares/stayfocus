package com.dculus.stayfocused.core.ui.charts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartMathTest {
    @Test
    fun clampedFractionHandlesBadInput() {
        assertEquals(0.5f, clampedFraction(10f, 20f), 0f)
        assertEquals(1f, clampedFraction(99f, 20f), 0f)
        assertEquals(0f, clampedFraction(-5f, 20f), 0f)
        assertEquals(0f, clampedFraction(5f, 0f), 0f)
        assertEquals(0f, clampedFraction(5f, -1f), 0f)
        assertEquals(0f, clampedFraction(Float.NaN, 20f), 0f)
        assertEquals(0f, clampedFraction(5f, Float.NaN), 0f)
    }

    @Test
    fun areaChartYLeavesHeadroomAndBaselineInset() {
        // 72 dp chart: 0 sits 4 dp above the bottom, max 14 dp below the top edge's full span (top 10 dp free).
        assertEquals(68f, areaChartY(0, 20), 0f)
        assertEquals(10f, areaChartY(20, 20), 0f)
        assertEquals(39f, areaChartY(10, 20), 0f)
        assertEquals(10f, areaChartY(99, 20), 0f)
        assertEquals(68f, areaChartY(-5, 20), 0f)
        assertEquals(68f, areaChartY(5, 0), 0f)
    }

    @Test
    fun areaChartXSpreadsPointsEvenly() {
        assertEquals(0f, areaChartX(0, 24, 230f), 0f)
        assertEquals(230f, areaChartX(23, 24, 230f), 0f)
        assertEquals(100f, areaChartX(1, 3, 200f), 0f)
        assertEquals(0f, areaChartX(0, 1, 200f), 0f)
    }

    @Test
    fun progressTicksLightProportionally() {
        assertEquals(0, (0 until TICK_PROGRESS_COUNT).count { isProgressTickLit(it, 0f) })
        assertEquals(20, (0 until TICK_PROGRESS_COUNT).count { isProgressTickLit(it, 0.5f) })
        assertEquals(40, (0 until TICK_PROGRESS_COUNT).count { isProgressTickLit(it, 1f) })
        assertEquals(40, (0 until TICK_PROGRESS_COUNT).count { isProgressTickLit(it, 3f) })
        assertEquals(0, (0 until TICK_PROGRESS_COUNT).count { isProgressTickLit(it, -1f) })
        assertEquals(0, (0 until TICK_PROGRESS_COUNT).count { isProgressTickLit(it, Float.NaN) })
        assertTrue(isProgressTickLit(0, 0.01f))
        assertFalse(isProgressTickLit(1, 0.025f))
    }

    @Test
    fun hourTicksUseHoursSixToTwentyThree() {
        val kid = ChartSamples.kidHourly
        val minutes = hourTickMinutes(kid)
        assertEquals(18, minutes.size)
        assertEquals(kid.subList(6, 24), minutes)
        assertEquals(18 * HOUR_TICKS_PER_HOUR, minutes.size * HOUR_TICKS_PER_HOUR)
    }

    @Test
    fun hourTicksTolerateShortAndNegativeInput() {
        assertEquals(List(18) { 0 }, hourTickMinutes(emptyList()))
        assertEquals(listOf(0, 7) + List(16) { 0 }, hourTickMinutes(List(6) { 3 } + listOf(-2, 7)))
    }

    @Test
    fun hourTickHeightIsFlooredAndCapped() {
        assertEquals(3f, hourTickHeightDp(0), 0f)
        assertEquals(3f, hourTickHeightDp(1), 0f)
        assertEquals(34f, hourTickHeightDp(25), 0f)
        assertEquals(34f, hourTickHeightDp(400), 0f)
        assertEquals(12 / 25f * 34f, hourTickHeightDp(12), 0.001f)
        assertEquals(3f, hourTickHeightDp(-5), 0f)
    }
}
