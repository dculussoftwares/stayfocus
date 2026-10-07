package com.dculus.stayfocused.core.model

import kotlin.math.roundToInt

/** One column of the LED bar chart: [level] lit cells (0..rows), [isCurrent] marks the current hour. */
data class LedColumn(
    val level: Int,
    val isCurrent: Boolean = false,
)

/**
 * Lit level per hour: `max(v > 0 ? 1 : 0, round(v / max * rows))`. Hours after [currentHour] are 0
 * (null = a finished day, nothing is in the future). The [currentHour] column is flagged.
 */
fun ledLevels(
    hourly: List<Int>,
    rows: Int,
    currentHour: Int?,
): List<LedColumn> {
    val max = hourly.maxOrNull()?.takeIf { it > 0 } ?: 0
    return hourly.mapIndexed { i, v ->
        val future = currentHour != null && i > currentHour
        val level =
            if (future || max == 0 || v <= 0) {
                0
            } else {
                maxOf(1, (v.toDouble() / max * rows).roundToInt()).coerceAtMost(rows)
            }
        LedColumn(level, isCurrent = i == currentHour)
    }
}
