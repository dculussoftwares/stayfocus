@file:Suppress("MagicNumber")

package com.dculus.stayfocused.feature.insights

import java.time.LocalDate

private val PreviewApps =
    listOf(
        "Instagram" to 41,
        "YouTube" to 33,
        "Reddit" to 24,
        "WhatsApp" to 18,
        "Chrome" to 12,
        "Snapchat" to 8,
    )

private val PreviewHours = listOf(0, 0, 0, 0, 0, 0, 2, 6, 14, 9, 5, 8, 12, 4, 3, 7, 10, 6, 9, 18, 22, 15, 8, 2)
private val PreviewWeek = listOf(190, 150, 210, 120, 175, 230, 147)

/** Sample state for previews and screenshots; the reference PNGs use the same shape of data. */
internal fun previewInsightsState(
    range: InsightsRange,
    metric: InsightsMetric = InsightsMetric.ScreenTime,
    offset: Int = 0,
    empty: Boolean = false,
): InsightsUiState {
    val apps =
        PreviewApps.mapIndexed { i, (name, mins) ->
            RankedApp(i + 1, "com.example.${name.lowercase()}", name, mins, mins.toFloat() / PreviewApps.first().second)
        }
    val date = LocalDate.of(2026, 10, 8).plusDays(offset.toLong())
    val day =
        if (range == InsightsRange.Day) {
            InsightsDay(
                range = range,
                heroMins = 153,
                values = PreviewHours,
                chartMax = DAY_CHART_MIN_MAX,
                average = 153f / HOURS_PER_DAY,
                highlightIndex = if (offset == 0) 21 else null,
                axisLabels = emptyList(),
                apps = apps,
            )
        } else {
            InsightsDay(
                range = range,
                heroMins = Math.round(PreviewWeek.sum() / 7f),
                values = PreviewWeek,
                chartMax = WEEK_CHART_MIN_MAX,
                average = PreviewWeek.sum() / 7f,
                highlightIndex = 6,
                axisLabels = weekAxisLabels(date, java.util.Locale.ENGLISH),
                apps = apps,
            )
        }
    return InsightsUiState(
        range = range,
        dayOffset = offset,
        date = date,
        metric = metric,
        loading = false,
        day = if (empty) null else day,
        unlocksToday = 41,
        blockedToday = 23,
    )
}
