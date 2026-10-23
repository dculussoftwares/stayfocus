@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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

/** Stand-in for the Insights card's headline row, so the "Avg" pill (drawn above the bars) has room. */
@Composable
private fun InsightsHeadline() {
    Column {
        Text("Screen time today", style = StayFocusedTheme.type.bodyL, color = StayFocusedTheme.colors.onGradient)
        Spacer(Modifier.height(80.dp))
    }
}

/** Home hero (05-home.png): the violet card with the dot grid of `ME_H`. */
@Preview(widthDp = 390, heightDp = 260)
@Composable
internal fun HomeDotGridPreview() {
    ChartPreviewSurface {
        ChartCard(V5Gradients.violet.adaptive) {
            DotGrid(
                values = ChartSamples.meHourly,
                max = 20,
                color = StayFocusedTheme.colors.onGradient,
                contentDescription = "Screen time by hour, peak 19 min at 9 PM",
                highlightIndex = ChartSamples.ME_HIGHLIGHT_HOUR,
            )
        }
    }
}

/** Family child hero (17-family.png): the pink card with the dot grid of `KID_H` and the hour ticks. */
@Preview(widthDp = 390, heightDp = 400)
@Composable
internal fun ChildHourTicksPreview() {
    ChartPreviewSurface {
        ChartCard(V5Gradients.pinkHero.adaptive) {
            DotGrid(
                values = ChartSamples.kidHourly,
                max = 25,
                color = StayFocusedTheme.colors.onGradient,
                contentDescription = "Screen time by hour, peak 25 min at 5 PM",
                highlightIndex = ChartSamples.ME_HIGHLIGHT_HOUR,
            )
        }
        ChartCard(SolidColor(StayFocusedTheme.colors.surface)) {
            HourTicks(ChartSamples.kidHourly, contentDescription = "Screen time by hour, peak 25 min at 5 PM")
        }
    }
}

/** Insights, day (21-insights-day.png). */
@Preview(widthDp = 390, heightDp = 400)
@Composable
internal fun InsightsDayChartPreview() {
    ChartPreviewSurface {
        ChartCard(V5Gradients.violet.adaptive) {
            InsightsHeadline()
            GlassBarChart(
                values = ChartSamples.meHourly,
                max = 20,
                average = ChartSamples.meHourly.sum() / 24f,
                averageLabel = "6m/h",
                axisLabels = ChartSamples.dayAxis,
                contentDescription = "Screen time by hour, peak 19 min at 9 PM",
                highlightIndex = ChartSamples.ME_HIGHLIGHT_HOUR,
                mode = GlassBarMode.Day,
            )
        }
    }
}

/** Insights, week (22-insights-week.png). */
@Preview(widthDp = 390, heightDp = 400)
@Composable
internal fun InsightsWeekChartPreview() {
    ChartPreviewSurface {
        ChartCard(V5Gradients.violet.adaptive) {
            InsightsHeadline()
            GlassBarChart(
                values = ChartSamples.week,
                max = 240,
                average = ChartSamples.week.sum() / 7f,
                averageLabel = "2h 56m",
                axisLabels = ChartSamples.weekAxis,
                contentDescription = "Screen time by day, peak 3 h 40 min on Saturday",
                highlightIndex = 6,
                mode = GlassBarMode.Week,
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
@Preview(widthDp = 390, heightDp = 1060)
@Composable
internal fun ChartEdgeCasesPreview() {
    ChartPreviewSurface {
        ChartCard(V5Gradients.violet.adaptive) {
            DotGrid(
                List(24) { 0 },
                max = 20,
                color = StayFocusedTheme.colors.onGradient,
                contentDescription = "No screen time",
            )
            DotGrid(
                listOf(99, 20, -3, 5),
                modifier = Modifier.width(80.dp),
                max = 0,
                color = StayFocusedTheme.colors.onGradient,
                contentDescription = "Nothing to scale",
            )
            InsightsHeadline()
            GlassBarChart(
                values = List(24) { 0 },
                max = 20,
                average = 0f,
                averageLabel = "0m/h",
                axisLabels = ChartSamples.dayAxis,
                contentDescription = "No screen time",
            )
            InsightsHeadline()
            GlassBarChart(
                values = listOf(500, 30, 0, 12),
                max = 20,
                average = 400f,
                averageLabel = "9m/h",
                axisLabels = ChartSamples.dayAxis,
                contentDescription = "Values above the maximum",
                highlightIndex = 0,
            )
        }
        ChartCard(SolidColor(StayFocusedTheme.colors.surface)) {
            HourTicks(emptyList(), contentDescription = "No screen time")
            TickProgress(2.5f, contentDescription = "Over the limit")
            TickProgress(Float.NaN, contentDescription = "Unknown")
        }
    }
}
