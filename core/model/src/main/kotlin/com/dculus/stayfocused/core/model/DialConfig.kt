package com.dculus.stayfocused.core.model

data class DialConfig(val max: Int, val step: Int, val presets: List<Int>)

object DialConfigs {
    val Break = DialConfig(max = 120, step = 5, presets = listOf(15, 30, 60, 120))
    val Limit = DialConfig(max = 180, step = 5, presets = listOf(15, 30, 60, 90))
    val CycleUse = DialConfig(max = 60, step = 1, presets = listOf(5, 10, 15, 30))
    val CycleRest = DialConfig(max = 180, step = 5, presets = listOf(15, 30, 60, 120))
    val BlockNow = DialConfig(max = 480, step = 15, presets = listOf(30, 60, 120, 240))
}
