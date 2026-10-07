package com.dculus.stayfocused.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class DialTest {
    private val configs =
        mapOf(
            "Break" to DialConfigs.Break,
            "Limit" to DialConfigs.Limit,
            "CycleUse" to DialConfigs.CycleUse,
            "CycleRest" to DialConfigs.CycleRest,
            "BlockNow" to DialConfigs.BlockNow,
        )

    @Test
    fun topOfDialMapsToMax() {
        configs.forEach { (n, c) ->
            assertEquals(c.max, dialValueForAngle(0.0, c), "$n 0deg")
            assertEquals(c.max, dialValueForAngle(359.0, c), "$n 359deg")
            assertEquals(c.max, dialValueForAngle(360.0, c), "$n 360deg")
        }
    }

    @Test
    fun firstStepAngleGivesOneStepAndSubStepAngleWrapsToMax() {
        configs.forEach { (n, c) ->
            val stepAngle = 360.0 * c.step / c.max
            assertEquals(c.step, dialValueForAngle(stepAngle, c), "$n one step")
            assertEquals(c.max, dialValueForAngle(stepAngle * 0.4, c), "$n under half a step reads as 0 -> max")
        }
    }

    @Test
    fun presetAnglesRoundTrip() {
        configs.forEach { (n, c) ->
            c.presets.forEach { p ->
                val angle = p.toDouble() / c.max * 360.0
                assertEquals(p, dialValueForAngle(angle, c), "$n preset $p")
            }
        }
    }

    @Test
    fun quarterTurns() {
        assertEquals(30, dialValueForAngle(90.0, DialConfigs.Break))
        assertEquals(60, dialValueForAngle(180.0, DialConfigs.Break))
        assertEquals(90, dialValueForAngle(270.0, DialConfigs.Break))
        assertEquals(240, dialValueForAngle(180.0, DialConfigs.BlockNow))
        assertEquals(15, dialValueForAngle(90.0, DialConfigs.CycleUse))
    }

    @Test
    fun valuesAreAlwaysMultiplesOfStepWithinRange() {
        configs.forEach { (n, c) ->
            var a = 0.0
            while (a < 360.0) {
                val v = dialValueForAngle(a, c)
                assertEquals(0, v % c.step, "$n $a")
                assert(v in c.step..c.max) { "$n $a -> $v" }
                a += 0.7
            }
        }
    }

    @Test
    fun negativeAndWrappedAnglesNormalise() {
        assertEquals(dialValueForAngle(270.0, DialConfigs.Break), dialValueForAngle(-90.0, DialConfigs.Break))
        assertEquals(dialValueForAngle(90.0, DialConfigs.Break), dialValueForAngle(450.0, DialConfigs.Break))
    }

    @Test
    fun centerLabelAndUnit() {
        listOf(
            5 to ("5" to DialUnit.MINUTES),
            45 to ("45" to DialUnit.MINUTES),
            59 to ("59" to DialUnit.MINUTES),
            60 to ("1" to DialUnit.HOUR),
            90 to ("1:30" to DialUnit.HOURS),
            120 to ("2" to DialUnit.HOURS),
            135 to ("2:15" to DialUnit.HOURS),
            480 to ("8" to DialUnit.HOURS),
        ).forEach { (v, e) ->
            assertEquals(e.first, dialCenterLabel(v), "label $v")
            assertEquals(e.second, dialUnit(v), "unit $v")
        }
    }
}
