@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.charts

import kotlin.math.max

/** `value / max` clamped to 0..1; a non-positive or non-finite [max] or a negative [value] gives 0 (never crashes). */
internal fun clampedFraction(
    value: Float,
    max: Float,
): Float {
    if (!value.isFinite() || !max.isFinite() || max <= 0f) return 0f
    return (value / max).coerceIn(0f, 1f)
}

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

internal const val AREA_CHART_HEIGHT_DP = 72f
internal const val AREA_CHART_BASELINE_INSET_DP = 4f
internal const val AREA_CHART_HEADROOM_DP = 10f

/**
 * Y position (dp, from the top) of [value] in a 72 dp [SmoothAreaChart]: 4 dp above the baseline at 0, and
 * `height - 14` dp higher at [max], so the top 10 dp stays free. Values are clamped to 0..[max].
 */
internal fun areaChartY(
    value: Int,
    max: Int,
): Float =
    AREA_CHART_HEIGHT_DP - AREA_CHART_BASELINE_INSET_DP -
        clampedFraction(value.toFloat(), max.toFloat()) *
        (AREA_CHART_HEIGHT_DP - AREA_CHART_BASELINE_INSET_DP - AREA_CHART_HEADROOM_DP)

/** X position of point [index] of [count] evenly spaced across [width]; a single point sits at 0. */
internal fun areaChartX(
    index: Int,
    count: Int,
    width: Float,
): Float = if (count <= 1) 0f else index * width / (count - 1)
