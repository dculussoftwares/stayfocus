@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.v5.charts

import kotlin.math.max
import kotlin.math.roundToInt

/** `value / max` clamped to 0..1; a non-positive or non-finite [max] or a negative [value] gives 0 (never crashes). */
internal fun clampedFraction(
    value: Float,
    max: Float,
): Float {
    if (!value.isFinite() || !max.isFinite() || max <= 0f) return 0f
    return (value / max).coerceIn(0f, 1f)
}

/** Lit dots in a [DotGrid] column: `round(min(1, v / max) × rows)` (prototype `dotGrid()`). */
internal fun dotGridLitCount(
    value: Int,
    max: Int,
    rows: Int,
): Int = (clampedFraction(value.toFloat(), max.toFloat()) * rows).roundToInt().coerceIn(0, rows)

internal const val TICK_PROGRESS_COUNT = 40

/** Whether tick [index] of a [TickProgress] is lit: `index / 40 < min(1, fraction)` (prototype `viewBlock()`). */
internal fun isProgressTickLit(
    index: Int,
    fraction: Float,
): Boolean = index.toFloat() / TICK_PROGRESS_COUNT < fraction

internal const val HOUR_TICKS_FIRST_HOUR = 6
internal const val HOUR_TICKS_END_HOUR = 24
internal const val HOUR_TICKS_PER_HOUR = 3
internal const val HOUR_TICKS_FULL_MINUTES = 25
internal const val HOUR_TICKS_MAX_DP = 34f
internal const val HOUR_TICKS_MIN_DP = 3f

/** Minutes for each hour 6..23 of [hourlyMins] (index = hour of day); missing hours count as 0, negatives as 0. */
internal fun hourTickMinutes(hourlyMins: List<Int>): List<Int> =
    (HOUR_TICKS_FIRST_HOUR until HOUR_TICKS_END_HOUR).map { hour -> max(0, hourlyMins.getOrElse(hour) { 0 }) }

/** Tick height in dp for [minutes] in an hour: `max(3, v / 25 × 34)`, capped at the 34 dp row. */
internal fun hourTickHeightDp(minutes: Int): Float =
    max(HOUR_TICKS_MIN_DP, minutes.coerceAtLeast(0) / HOUR_TICKS_FULL_MINUTES.toFloat() * HOUR_TICKS_MAX_DP)
        .coerceAtMost(HOUR_TICKS_MAX_DP)

/** Day = 24 hourly bars (gap 4, radius 5); Week = 7 daily bars (gap 10, radius 16). */
enum class GlassBarMode(
    internal val gapDp: Float,
    internal val radiusDp: Float,
) {
    Day(gapDp = 4f, radiusDp = 5f),
    Week(gapDp = 10f, radiusDp = 16f),
}

internal const val BAR_HIGH_MINUTES = 12
internal const val DAY_BAR_MIN_FRACTION = 0.08f
internal const val AVERAGE_LINE_MAX_FRACTION = 0.96f

/**
 * Height of a bar as a fraction of its track. Day: empty stays empty (also without a valid [max]), else at least 8 %;
 * week: proportional.
 */
internal fun barFraction(
    value: Int,
    max: Int,
    mode: GlassBarMode,
): Float {
    val f = clampedFraction(value.toFloat(), max.toFloat())
    return when (mode) {
        GlassBarMode.Day -> if (f > 0f) max(DAY_BAR_MIN_FRACTION, f) else 0f
        GlassBarMode.Week -> f
    }
}

/** Position of the dashed average line from the bottom of the track, capped at 96 % like the prototype. */
internal fun averageLineFraction(
    average: Float,
    max: Int,
): Float = clampedFraction(average, max.toFloat()).coerceAtMost(AVERAGE_LINE_MAX_FRACTION)

/** Fill style of one bar (V5_SPEC §4): the current one is orange, day bars above 12 min/h are bright. */
internal enum class BarStyle { Current, High, Low }

internal fun barStyle(
    index: Int,
    value: Int,
    highlightIndex: Int?,
    mode: GlassBarMode,
): BarStyle =
    when {
        index == highlightIndex -> BarStyle.Current
        mode == GlassBarMode.Week || value > BAR_HIGH_MINUTES -> BarStyle.High
        else -> BarStyle.Low
    }
