@file:Suppress("MagicNumber")

package com.dculus.stayfocused.feature.insights

import com.dculus.stayfocused.core.model.ledLevels
import java.time.LocalDate

private fun previewHours(metric: InsightsMetric): List<Int> =
    if (metric == InsightsMetric.Unlocks) {
        listOf(0, 0, 0, 0, 0, 1, 3, 5, 4, 2, 3, 2, 4, 2, 1, 2, 3, 2, 3, 4, 5, 3, 1, 0)
    } else {
        listOf(0, 0, 0, 0, 0, 0, 2, 6, 14, 9, 5, 8, 12, 4, 3, 7, 10, 6, 9, 18, 22, 15, 8, 2)
    }

internal fun previewInsightsState(
    metric: InsightsMetric,
    offset: Int = 0,
    empty: Boolean = false,
): InsightsUiState {
    val hours = previewHours(metric)
    val apps =
        listOf("Instagram" to 41, "YouTube" to 33, "Reddit" to 24, "WhatsApp" to 18, "Chrome" to 12)
            .mapIndexed { i, (name, v) ->
                val value = if (metric == InsightsMetric.ScreenTime) v else v / 2
                RankedApp(
                    i + 1,
                    "com.example.${name.lowercase()}",
                    name,
                    value,
                    value / 41f * if (metric == InsightsMetric.ScreenTime) 1f else 2f,
                )
            }
    val total =
        if (metric == InsightsMetric.ScreenTime) {
            147
        } else if (metric == InsightsMetric.Opens) {
            63
        } else {
            48
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
                    average =
                        if (metric ==
                            InsightsMetric.ScreenTime
                        ) {
                            139
                        } else if (metric == InsightsMetric.Opens) {
                            58
                        } else {
                            51
                        },
                    levels = ledLevels(hours, LED_ROWS, if (offset == 0) 10 else null),
                    avgFraction =
                        if (metric ==
                            InsightsMetric.Unlocks
                        ) {
                            51f / 24f / hours.max()
                        } else {
                            139f / 24f / hours.max()
                        },
                    apps = apps,
                )
            },
    )
}
