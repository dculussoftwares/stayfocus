package com.dculus.stayfocused.core.ui.v5.charts

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
    fun dotGridLitCountMatchesPrototype() {
        // Prototype: round(min(1, v / max) × rows), ME_H with max 20 and 6 rows.
        assertEquals(0, dotGridLitCount(0, 20, 6))
        assertEquals(1, dotGridLitCount(4, 20, 6))
        assertEquals(3, dotGridLitCount(9, 20, 6))
        assertEquals(6, dotGridLitCount(19, 20, 6))
        assertEquals(6, dotGridLitCount(500, 20, 6))
        assertEquals(0, dotGridLitCount(-4, 20, 6))
        assertEquals(0, dotGridLitCount(5, 0, 6))
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

    @Test
    fun dayBarsHaveAMinimumHeightButZeroStaysEmpty() {
        assertEquals(0f, barFraction(0, 20, GlassBarMode.Day), 0f)
        assertEquals(0.08f, barFraction(1, 20, GlassBarMode.Day), 0f)
        assertEquals(0.5f, barFraction(10, 20, GlassBarMode.Day), 0f)
        assertEquals(1f, barFraction(500, 20, GlassBarMode.Day), 0f)
        assertEquals(0f, barFraction(5, 0, GlassBarMode.Day), 0f)
        assertEquals(0f, barFraction(5, -3, GlassBarMode.Day), 0f)
    }

    @Test
    fun weekBarsAreProportionalAndClamped() {
        assertEquals(182 / 240f, barFraction(182, 240, GlassBarMode.Week), 0.0001f)
        assertEquals(1f, barFraction(300, 240, GlassBarMode.Week), 0f)
        assertEquals(0f, barFraction(0, 240, GlassBarMode.Week), 0f)
        assertEquals(0f, barFraction(10, 0, GlassBarMode.Week), 0f)
    }

    @Test
    fun averageLineIsCappedAtNinetySixPercent() {
        assertEquals(153 / 24f / 20f, averageLineFraction(153 / 24f, 20), 0.0001f)
        assertEquals(0.96f, averageLineFraction(400f, 20), 0f)
        assertEquals(0f, averageLineFraction(0f, 20), 0f)
        assertEquals(0f, averageLineFraction(5f, 0), 0f)
    }

    @Test
    fun barStyleFollowsThePrototype() {
        assertEquals(BarStyle.Current, barStyle(21, 14, 21, GlassBarMode.Day))
        assertEquals(BarStyle.Current, barStyle(21, 0, 21, GlassBarMode.Day))
        assertEquals(BarStyle.High, barStyle(17, 18, 21, GlassBarMode.Day))
        assertEquals(BarStyle.Low, barStyle(11, 7, 21, GlassBarMode.Day))
        assertEquals(BarStyle.High, barStyle(12, 15, 21, GlassBarMode.Day))
        assertEquals(BarStyle.High, barStyle(0, 13, null, GlassBarMode.Day))
        assertEquals(BarStyle.Low, barStyle(0, 12, null, GlassBarMode.Day))
        assertEquals(BarStyle.High, barStyle(2, 0, 6, GlassBarMode.Week))
        assertEquals(BarStyle.Current, barStyle(6, 153, 6, GlassBarMode.Week))
    }
}
