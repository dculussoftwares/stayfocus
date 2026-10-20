package com.dculus.stayfocused.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RulerScaleTest {
    private val scales =
        mapOf(
            "limit" to RulerScales.Limit,
            "use" to RulerScales.Use,
            "rest" to RulerScales.Rest,
            "now" to RulerScales.Now,
            "break" to RulerScales.Break,
        )

    @Test
    fun presetsMatchTheSpecRanges() {
        assertEquals(RulerScale(5, 240, 5), RulerScales.Limit)
        assertEquals(RulerScale(1, 60, 1), RulerScales.Use)
        assertEquals(RulerScale(5, 180, 5), RulerScales.Rest)
        assertEquals(RulerScale(15, 480, 15), RulerScales.Now)
        assertEquals(RulerScale(5, 120, 5), RulerScales.Break)
    }

    @Test
    fun edgesMapToMinAndMax() {
        scales.forEach { (n, s) ->
            assertEquals(s.min, s.valueAt(0.0), "$n left edge")
            assertEquals(s.max, s.valueAt(1.0), "$n right edge")
        }
    }

    @Test
    fun fractionsOutsideZeroToOneClamp() {
        scales.forEach { (n, s) ->
            assertEquals(s.min, s.valueAt(-3.5), "$n below")
            assertEquals(s.max, s.valueAt(42.0), "$n above")
            assertEquals(s.min, s.valueAt(Double.NEGATIVE_INFINITY), "$n -inf")
            assertEquals(s.max, s.valueAt(Double.POSITIVE_INFINITY), "$n +inf")
        }
    }

    @Test
    fun nanFractionFallsBackToMin() {
        assertEquals(5, RulerScales.Limit.valueAt(Double.NaN))
    }

    @Test
    fun middleOfEachRangeSnapsToTheStep() {
        assertEquals(125, RulerScales.Limit.valueAt(0.5)) // 122.5 -> 125 (round half up)
        assertEquals(31, RulerScales.Use.valueAt(0.5)) // 30.5 -> 31
        assertEquals(95, RulerScales.Rest.valueAt(0.5)) // 92.5 -> 95
        assertEquals(255, RulerScales.Now.valueAt(0.5)) // 247.5 -> 255
        assertEquals(65, RulerScales.Break.valueAt(0.5)) // 62.5 -> 65
    }

    @Test
    fun everyValueIsAMultipleOfTheStepInsideTheRange() {
        scales.forEach { (n, s) ->
            for (i in 0..1000) {
                val v = s.valueAt(i / 1000.0)
                assertEquals(0, v % s.step, "$n $i")
                assertTrue(v in s.min..s.max, "$n $i -> $v")
            }
        }
    }

    @Test
    fun valueAtIsMonotonic() {
        scales.forEach { (n, s) ->
            var last = s.min
            for (i in 0..1000) {
                val v = s.valueAt(i / 1000.0)
                assertTrue(v >= last, "$n $i")
                last = v
            }
        }
    }

    @Test
    fun fractionOfIsInverseOfValueAtForEveryStep() {
        scales.forEach { (n, s) ->
            var v = s.min
            while (v <= s.max) {
                assertEquals(v, s.valueAt(s.fractionOf(v)), "$n $v")
                v += s.step
            }
        }
    }

    @Test
    fun fractionOfBoundariesAndClamping() {
        assertEquals(0.0, RulerScales.Limit.fractionOf(5), 1e-9)
        assertEquals(1.0, RulerScales.Limit.fractionOf(240), 1e-9)
        assertEquals(0.0, RulerScales.Limit.fractionOf(-10), 1e-9)
        assertEquals(1.0, RulerScales.Limit.fractionOf(9999), 1e-9)
        assertEquals(0.5, RulerScales.Break.fractionOf(62), 0.01)
    }

    @Test
    fun ticksHave45EntriesWithPassedCurrentAhead() {
        val ticks = RulerScales.Break.ticks(RulerScales.Break.min)
        assertEquals(45, ticks.size)
        assertEquals(RulerTick.Current, ticks.first())
        assertTrue(ticks.drop(1).all { it == RulerTick.Ahead })
    }

    @Test
    fun ticksAtMaxMarkLastAsCurrent() {
        val ticks = RulerScales.Limit.ticks(240)
        assertEquals(RulerTick.Current, ticks.last())
        assertTrue(ticks.dropLast(1).all { it == RulerTick.Passed })
    }

    @Test
    fun ticksCurrentIndexIsRoundOfFractionTimes44() {
        // 45 of 5..240 -> p = 40/235 = .1702 -> round(7.49) = 7
        val ticks = RulerScales.Limit.ticks(45)
        assertEquals(7, ticks.indexOf(RulerTick.Current))
        assertEquals(1, ticks.count { it == RulerTick.Current })
        assertTrue(ticks.take(7).all { it == RulerTick.Passed })
        assertTrue(ticks.drop(8).all { it == RulerTick.Ahead })
        // 30 of 5..120 -> p = 25/115 = .2174 -> round(9.57) = 10
        assertEquals(10, RulerScales.Break.ticks(30).indexOf(RulerTick.Current))
    }

    @Test
    fun ticksClampOutOfRangeValues() {
        assertEquals(0, RulerScales.Now.ticks(-100).indexOf(RulerTick.Current))
        assertEquals(44, RulerScales.Now.ticks(10_000).indexOf(RulerTick.Current))
    }

    @Test
    fun ticksHonourACustomCount() {
        val ticks = RulerScales.Use.ticks(30, n = 10)
        assertEquals(11, ticks.size)
        assertEquals(5, ticks.indexOf(RulerTick.Current)) // p = 29/59 = .49 -> round(4.92) = 5
    }

    @Test
    fun ticksRejectANonPositiveCount() {
        assertFailsWith<IllegalArgumentException> { RulerScales.Use.ticks(30, n = 0) }
    }

    @Test
    fun stepByMovesWholeStepsAndClamps() {
        val s = RulerScales.Limit
        assertEquals(50, s.stepBy(45, 1))
        assertEquals(40, s.stepBy(45, -1))
        assertEquals(5, s.stepBy(5, -1))
        assertEquals(240, s.stepBy(240, 1))
        assertEquals(240, s.stepBy(235, 10))
        assertEquals(5, s.stepBy(10, -10))
    }

    @Test
    fun stepByWithZeroStepsReturnsTheClampedValue() {
        assertEquals(240, RulerScales.Limit.stepBy(1000, 0))
        assertEquals(60, RulerScales.Now.stepBy(60, 0))
    }

    @Test
    fun snapRoundsToTheNearestStepAndClamps() {
        assertEquals(45, RulerScales.Limit.snap(46))
        assertEquals(50, RulerScales.Limit.snap(48))
        assertEquals(5, RulerScales.Limit.snap(0))
        assertEquals(240, RulerScales.Limit.snap(999))
        assertEquals(37, RulerScales.Use.snap(37))
    }

    @Test
    fun stepsBetweenEndsMatchTheRangeLength() {
        assertEquals(47, RulerScales.Limit.stepCount)
        assertEquals(59, RulerScales.Use.stepCount)
        assertEquals(35, RulerScales.Rest.stepCount)
        assertEquals(31, RulerScales.Now.stepCount)
        assertEquals(23, RulerScales.Break.stepCount)
    }

    @Test
    fun invalidScalesAreRejected() {
        assertFailsWith<IllegalArgumentException> { RulerScale(10, 10, 5) }
        assertFailsWith<IllegalArgumentException> { RulerScale(10, 5, 5) }
        assertFailsWith<IllegalArgumentException> { RulerScale(5, 10, 0) }
        assertFailsWith<IllegalArgumentException> { RulerScale(5, 10, -1) }
    }

    @Test
    fun endpointsMustBeMultiplesOfTheStep() {
        assertFailsWith<IllegalArgumentException> { RulerScale(1, 10, 2) }
        assertFailsWith<IllegalArgumentException> { RulerScale(1, 10, 3) }
        assertFailsWith<IllegalArgumentException> { RulerScale(0, 10, 3) }
    }

    @Test
    fun fractionalTargetsSnapToTheNearestStep() {
        assertEquals(50, RulerScales.Limit.snap(47.5))
        assertEquals(45, RulerScales.Limit.snap(47.4))
        assertEquals(240, RulerScales.Limit.snap(1e9))
        assertEquals(5, RulerScales.Limit.snap(Double.NaN))
    }
}
