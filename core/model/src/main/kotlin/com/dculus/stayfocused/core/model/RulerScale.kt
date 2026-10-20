package com.dculus.stayfocused.core.model

import kotlin.math.floor

/** How a tick of the v5 ruler relates to the current value. */
enum class RulerTick { Passed, Current, Ahead }

/**
 * Range of a v5 tick ruler: values `min..max` in multiples of [step] (V5_SPEC §5). The maths mirrors the prototype's
 * `ruler()`: a pointer fraction `f` of the width is `round((min + f·(max − min)) / step) · step`, clamped.
 */
data class RulerScale(
    val min: Int,
    val max: Int,
    val step: Int,
) {
    init {
        require(step > 0) { "step must be positive" }
        require(max > min) { "max must be greater than min" }
        require(min % step == 0 && max % step == 0) { "min and max must be multiples of step" }
    }

    /** Number of whole steps from [min] to [max]. */
    val stepCount: Int get() = (max - min) / step

    /** Value under a pointer at [fraction] (0 = left edge, 1 = right edge) of the ruler; out-of-range and NaN clamp. */
    fun valueAt(fraction: Double): Int {
        val f = if (fraction.isNaN()) 0.0 else fraction.coerceIn(0.0, 1.0)
        return snap(min + f * (max - min))
    }

    /** Position of [value] on the ruler, 0..1 (clamped). */
    fun fractionOf(value: Int): Double = ((value - min).toDouble() / (max - min)).coerceIn(0.0, 1.0)

    /** [value] rounded to the nearest multiple of [step] (halves round up, like the prototype) and clamped. */
    fun snap(value: Int): Int = snap(value.toDouble())

    /** [snap] for a fractional target (e.g. an accessibility `SetProgress` value); NaN clamps to [min]. */
    fun snap(value: Double): Int =
        if (value.isNaN()) min else (floor(value / step + HALF) * step).coerceIn(min.toDouble(), max.toDouble()).toInt()

    /** [value] moved by [steps] whole steps (negative = down), clamped to the range. */
    fun stepBy(
        value: Int,
        steps: Int,
    ): Int = snap(value.toDouble() + steps.toDouble() * step)

    /**
     * The `n + 1` tick states for [value]: the tick at index `round(p · n)` is [RulerTick.Current], earlier ones
     * [RulerTick.Passed], later ones [RulerTick.Ahead].
     */
    fun ticks(
        value: Int,
        n: Int = DEFAULT_TICK_INTERVALS,
    ): List<RulerTick> {
        require(n > 0) { "n must be positive" }
        val current = roundHalfUp(fractionOf(value) * n)
        return List(n + 1) {
            when {
                it == current -> RulerTick.Current
                it < current -> RulerTick.Passed
                else -> RulerTick.Ahead
            }
        }
    }

    private fun roundHalfUp(x: Double): Int = floor(x + HALF).toInt()

    companion object {
        /** The prototype draws 45 ticks, i.e. 44 intervals. */
        const val DEFAULT_TICK_INTERVALS = 44
        private const val HALF = 0.5
    }
}

/** The rulers of V5_SPEC §5, in minutes. */
object RulerScales {
    val Limit = RulerScale(min = 5, max = 240, step = 5)
    val Use = RulerScale(min = 1, max = 60, step = 1)
    val Rest = RulerScale(min = 5, max = 180, step = 5)
    val Now = RulerScale(min = 15, max = 480, step = 15)
    val Break = RulerScale(min = 5, max = 120, step = 5)
}
