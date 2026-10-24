@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.ui.components.AppIconTile
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.ui.theme.V5Gradients

@Composable
private fun ChartCard(
    brush: Brush,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .clip(RoundedCornerShape(36.dp))
                .background(brush)
                .padding(horizontal = 22.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun ChartPreviewSurface(content: @Composable () -> Unit) {
    StayFocusedTheme {
        Column(
            Modifier.background(SolidColor(StayFocusedTheme.colors.background)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) { content() }
    }
}

/** Home hero (v6): the brand card with the day's [SmoothAreaChart]. */
@Preview(widthDp = 390, heightDp = 260)
@Composable
internal fun HomeAreaChartPreview() {
    ChartPreviewSurface {
        ChartCard(V5Gradients.brand.adaptive) {
            SmoothAreaChart(
                values = ChartSamples.meHourly,
                max = 20,
                axisLabels = ChartSamples.dayAxis,
                contentDescription = "Screen time by hour, peak 19 min at 9 PM",
                highlightIndex = ChartSamples.ME_HIGHLIGHT_HOUR,
            )
        }
    }
}

/** Family child hero (v6): the brand card with the child's area chart, and the hour ticks. */
@Preview(widthDp = 390, heightDp = 400)
@Composable
internal fun ChildHourTicksPreview() {
    ChartPreviewSurface {
        ChartCard(V5Gradients.brand.adaptive) {
            SmoothAreaChart(
                values = ChartSamples.kidHourly,
                max = 25,
                axisLabels = ChartSamples.dayAxis,
                contentDescription = "Screen time by hour, peak 25 min at 5 PM",
                highlightIndex = ChartSamples.ME_HIGHLIGHT_HOUR,
            )
        }
        ChartCard(SolidColor(StayFocusedTheme.colors.surface)) {
            HourTicks(ChartSamples.kidHourly, contentDescription = "Screen time by hour, peak 25 min at 5 PM")
        }
    }
}

/** Insights (v6): day (24 hourly points) and week (7 daily points, today highlighted). */
@Preview(widthDp = 390, heightDp = 400)
@Composable
internal fun InsightsAreaChartPreview() {
    ChartPreviewSurface {
        ChartCard(V5Gradients.brand.adaptive) {
            SmoothAreaChart(
                values = ChartSamples.meHourly,
                max = 20,
                axisLabels = ChartSamples.dayAxis,
                contentDescription = "Screen time by hour, peak 19 min at 9 PM",
                highlightIndex = ChartSamples.ME_HIGHLIGHT_HOUR,
            )
        }
        ChartCard(V5Gradients.brand.adaptive) {
            SmoothAreaChart(
                values = ChartSamples.week,
                max = 240,
                axisLabels = ChartSamples.weekAxis,
                contentDescription = "Screen time by day, peak 3 h 40 min on Saturday",
                highlightIndex = 6,
            )
        }
    }
}

/** Insights ranking rows (21-insights-day.png) and the block-card tick progress. */
@Preview(widthDp = 390, heightDp = 420)
@Composable
internal fun UsageRowsPreview() {
    ChartPreviewSurface {
        ChartCard(SolidColor(StayFocusedTheme.colors.surface)) {
            val apps =
                listOf(
                    Triple("com.instagram.android" to "Instagram", "52 min", 1f),
                    Triple("com.zhiliaoapp.musically" to "TikTok", "38 min", 38f / 52f),
                    Triple("com.google.android.youtube" to "YouTube", "31 min", 31f / 52f),
                )
            apps.forEach { (app, time, fraction) ->
                UsageRow(
                    icon = { AppIconTile(app.first, 38.dp, label = app.second) },
                    name = app.second,
                    timeText = time,
                    fraction = fraction,
                )
            }
        }
        ChartCard(SolidColor(StayFocusedTheme.colors.surface)) {
            TickProgress(0.55f, contentDescription = "1 h 6 min used of 2 h")
            TickProgress(0f, contentDescription = "Nothing used yet")
            TickProgress(1f, contentDescription = "Limit reached")
        }
    }
}

/** Empty data and values above `max` must render clamped, without crashing. */
@Preview(widthDp = 390, heightDp = 700)
@Composable
internal fun ChartEdgeCasesPreview() {
    ChartPreviewSurface {
        ChartCard(V5Gradients.brand.adaptive) {
            SmoothAreaChart(
                List(24) { 0 },
                max = 20,
                axisLabels = ChartSamples.dayAxis,
                contentDescription = "No screen time",
            )
            SmoothAreaChart(
                listOf(500, 30, 0, 12),
                max = 20,
                axisLabels = ChartSamples.dayAxis,
                contentDescription = "Values above the maximum",
                highlightIndex = 0,
            )
            SmoothAreaChart(
                listOf(99, 20, -3, 5),
                max = 0,
                axisLabels = listOf("a"),
                contentDescription = "Nothing to scale",
            )
        }
        ChartCard(SolidColor(StayFocusedTheme.colors.surface)) {
            HourTicks(emptyList(), contentDescription = "No screen time")
            TickProgress(2.5f, contentDescription = "Over the limit")
            TickProgress(Float.NaN, contentDescription = "Unknown")
        }
    }
}
