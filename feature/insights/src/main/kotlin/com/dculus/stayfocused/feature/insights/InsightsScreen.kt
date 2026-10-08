@file:Suppress("MagicNumber")

package com.dculus.stayfocused.feature.insights

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dculus.stayfocused.core.model.formatMinutes
import com.dculus.stayfocused.core.model.ledLevels
import com.dculus.stayfocused.core.ui.components.LedBarChart
import com.dculus.stayfocused.core.ui.components.MonoLabel
import com.dculus.stayfocused.core.ui.components.Panel
import com.dculus.stayfocused.core.ui.components.SegmentedTabs
import com.dculus.stayfocused.core.ui.icon.AppIcon
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import java.time.LocalDate

@Composable
internal fun InsightsRoute(viewModel: InsightsViewModel = hiltViewModel()) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    InsightsScreen(
        state = state,
        onEvent = { event ->
            when (event) {
                InsightsEvent.PreviousDay -> viewModel.previousDay()
                InsightsEvent.NextDay -> viewModel.nextDay()
                is InsightsEvent.SelectMetric -> viewModel.selectMetric(event.metric)
            }
        },
    )
}

private val MetricOrder = InsightsMetric.entries

@Composable
internal fun InsightsScreen(
    state: InsightsUiState,
    onEvent: (InsightsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = StayFocusedTheme.spacing
    val day = state.day
    LazyColumn(
        modifier = modifier.fillMaxSize().background(StayFocusedTheme.colors.background),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(horizontal = spacing.screen, vertical = spacing.gap16),
        verticalArrangement = Arrangement.Top,
    ) {
        item {
            Box(Modifier.padding(bottom = spacing.gap14)) {
                DayStepper(state, { onEvent(InsightsEvent.PreviousDay) }, { onEvent(InsightsEvent.NextDay) })
            }
        }
        item {
            Box(Modifier.padding(bottom = spacing.gap14)) {
                SegmentedTabs(
                    options =
                        listOf(
                            stringResource(R.string.insights_metric_screen_time),
                            stringResource(R.string.insights_metric_opens),
                            stringResource(R.string.insights_metric_unlocks),
                        ),
                    selectedIndex = MetricOrder.indexOf(state.metric),
                    onSelect = { onEvent(InsightsEvent.SelectMetric(MetricOrder[it])) },
                )
            }
        }
        when {
            state.loading -> {
                Unit
            }

            day == null -> {
                item { EmptyDay() }
            }

            else -> {
                item { Box(Modifier.padding(bottom = spacing.gap14)) { ChartPanel(day) } }
                if (day.apps.isEmpty()) {
                    item { Box(Modifier.padding(bottom = spacing.gap14)) { NoApps() } }
                } else {
                    itemsIndexed(day.apps, key = { _, app -> app.pkg }) { index, app ->
                        AppRow(app, day.metric, first = index == 0, last = index == day.apps.lastIndex)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayStepper(
    state: InsightsUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val c = StayFocusedTheme.colors
    val title =
        when (val t = dayTitle(state.dayOffset, state.date)) {
            DayTitle.Today -> stringResource(R.string.insights_day_today)
            DayTitle.Yesterday -> stringResource(R.string.insights_day_yesterday)
            is DayTitle.Weekday -> t.name
        }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton("‹", stringResource(R.string.insights_previous_day), state.canStepBack, onPrevious)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = StayFocusedTheme.type.title, color = c.text)
            Text(dayDateLabel(state.date), style = StayFocusedTheme.type.labelS, color = c.secondary)
        }
        StepButton("›", stringResource(R.string.insights_next_day), state.canStepForward, onNext)
    }
}

@Composable
private fun StepButton(
    glyph: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val c = StayFocusedTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .alpha(if (enabled) 1f else 0.3f)
                .clip(shape)
                .background(c.panel)
                .border(BorderStroke(1.dp, c.hairline10), shape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = StayFocusedTheme.type.title, color = c.text)
    }
}

private val AxisLabels = listOf("00", "06", "12", "18", "23")

@Composable
private fun ChartPanel(day: InsightsDay) {
    val c = StayFocusedTheme.colors
    val total =
        if (day.metric == InsightsMetric.ScreenTime) formatMinutes(day.total) else day.total.toString()
    val average =
        day.average?.let { if (day.metric == InsightsMetric.ScreenTime) formatMinutes(it) else it.toString() }
    val metricName = stringResource(day.metric.labelRes())
    val summary =
        listOfNotNull(
            "$metricName $total",
            average?.let { stringResource(R.string.insights_avg, it) },
            stringResource(R.string.insights_chart_summary, metricName),
        ).joinToString(". ")
    Panel(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = summary }) {
        Column(verticalArrangement = Arrangement.spacedBy(StayFocusedTheme.spacing.gap16)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(total, style = StayFocusedTheme.type.displayM, color = c.text, maxLines = 1)
                if (average != null) {
                    Text(
                        stringResource(R.string.insights_avg, average),
                        style = StayFocusedTheme.type.label,
                        color = c.secondary,
                        maxLines = 1,
                    )
                }
            }
            LedBarChart(
                levels = day.levels,
                rows = LED_ROWS,
                avgFraction = day.avgFraction,
                axisLabels = AxisLabels,
            )
        }
    }
}

@Composable
private fun AppRow(
    app: RankedApp,
    metric: InsightsMetric,
    first: Boolean,
    last: Boolean,
) {
    val c = StayFocusedTheme.colors
    val value = if (metric == InsightsMetric.ScreenTime) formatMinutes(app.value) else app.value.toString()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(rowShape(first, last))
            .background(c.panel)
            .padding(horizontal = 18.dp, vertical = if (first || last) 14.dp else 10.dp)
            .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(app.rankLabel, style = StayFocusedTheme.type.label, color = c.tertiary, modifier = Modifier.width(20.dp))
        AppIcon(pkg = app.pkg, label = app.label, size = 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    app.label,
                    style = StayFocusedTheme.type.body.copy(fontWeight = FontWeight.Bold),
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    value,
                    style = StayFocusedTheme.type.label.copy(fontWeight = FontWeight.SemiBold),
                    color = c.secondary,
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.track)
                    .drawBehind {
                        drawRoundRect(
                            color = c.accent,
                            size = Size(size.width * app.fraction.coerceIn(0f, 1f), size.height),
                            cornerRadius = CornerRadius(2.dp.toPx()),
                        )
                    },
            )
        }
    }
}

private fun rowShape(
    first: Boolean,
    last: Boolean,
) = RoundedCornerShape(
    topStart = if (first) 20.dp else 0.dp,
    topEnd = if (first) 20.dp else 0.dp,
    bottomStart = if (last) 20.dp else 0.dp,
    bottomEnd = if (last) 20.dp else 0.dp,
)

@Composable
private fun EmptyDay() {
    Panel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MonoLabel(stringResource(R.string.insights_empty_title))
            Text(
                stringResource(R.string.insights_empty_body),
                style = StayFocusedTheme.type.body,
                color = StayFocusedTheme.colors.secondary,
            )
        }
    }
}

@Composable
private fun NoApps() {
    Panel(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.insights_no_apps),
            style = StayFocusedTheme.type.body,
            color = StayFocusedTheme.colors.secondary,
        )
    }
}

private fun InsightsMetric.labelRes(): Int =
    when (this) {
        InsightsMetric.ScreenTime -> R.string.insights_metric_screen_time
        InsightsMetric.Opens -> R.string.insights_metric_opens
        InsightsMetric.Unlocks -> R.string.insights_metric_unlocks
    }

internal fun previewInsightsState(
    metric: InsightsMetric,
    offset: Int = 0,
    empty: Boolean = false,
): InsightsUiState {
    val hours =
        if (metric == InsightsMetric.Unlocks) {
            listOf(0, 0, 0, 0, 0, 1, 3, 5, 4, 2, 3, 2, 4, 2, 1, 2, 3, 2, 3, 4, 5, 3, 1, 0)
        } else {
            listOf(0, 0, 0, 0, 0, 0, 2, 6, 14, 9, 5, 8, 12, 4, 3, 7, 10, 6, 9, 18, 22, 15, 8, 2)
        }
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

@Preview(widthDp = 360, heightDp = 780, locale = "en")
@Composable
internal fun InsightsPreview() {
    StayFocusedTheme {
        InsightsScreen(previewInsightsState(InsightsMetric.ScreenTime), {})
    }
}
