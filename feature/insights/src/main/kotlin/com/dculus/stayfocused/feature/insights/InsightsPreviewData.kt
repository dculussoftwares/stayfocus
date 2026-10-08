@file:Suppress("MagicNumber")

package com.dculus.stayfocused.feature.insights

import com.dculus.stayfocused.core.model.ledLevels
import java.time.LocalDate

private fun previewHours(metric: InsightsMetric): List<Int> =
    if (metric == InsightsMetric.Unlocks) {
        listOf(0, 0, 0, 0, 0, 1, 2, 5, 4, 2, 3, 2, 4, 2, 1, 2, 3, 2, 3, 4, 4, 3, 1, 0)
    } else {
        listOf(0, 0, 0, 0, 0, 0, 2, 6, 14, 9, 5, 8, 12, 4, 3, 7, 10, 6, 9, 18, 22, 15, 8, 2)
    }

internal fun previewInsightsState(
    metric: InsightsMetric,
    offset: Int = 0,
    empty: Boolean = false,
): InsightsUiState {
    val hours = previewHours(metric)
    val values =
        when (metric) {
            InsightsMetric.ScreenTime -> listOf(41, 33, 24, 18, 12)
            InsightsMetric.Opens -> listOf(20, 16, 12, 9, 6)
            InsightsMetric.Unlocks -> listOf(20, 12, 8, 5, 3)
        }
    val names = listOf("Instagram", "YouTube", "Reddit", "WhatsApp", "Chrome")
    val apps =
        names.mapIndexed { i, name ->
            RankedApp(i + 1, "com.example.${name.lowercase()}", name, values[i], values[i].toFloat() / values.first())
        }
    // Opens totals sum the app values; screen time has other, smaller apps on top of the five shown.
    val total = if (metric == InsightsMetric.ScreenTime) 147 else values.sum()
    val average =
        if (metric == InsightsMetric.ScreenTime) {
            139
        } else if (metric == InsightsMetric.Opens) {
            58
        } else {
            51
        }
    val date = LocalDate.of(2026, 10, 8).plusDays(offset.toLong())
    return InsightsUiState(
        dayOffset = offset,
        date = date,
        metric = metric,
        loading = false,
        day =
            if (empty) {
                null
            } else {
                InsightsDay(
                    metric = metric,
                    total = total,
                    average = average,
                    levels = ledLevels(hours, LED_ROWS, if (offset == 0) 10 else null),
                    // Opens charts hourly foreground time, so its average line uses the screen-time average.
                    avgFraction = (if (metric == InsightsMetric.Unlocks) 51f else 139f) / 24f / hours.max(),
                    apps = apps,
                )
            },
    )
}
