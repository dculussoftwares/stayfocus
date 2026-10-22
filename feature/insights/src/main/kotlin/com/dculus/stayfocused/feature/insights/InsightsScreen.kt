@file:Suppress("MagicNumber")

package com.dculus.stayfocused.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dculus.stayfocused.core.model.formatMinutes
import com.dculus.stayfocused.core.ui.v5.charts.GlassBarChart
import com.dculus.stayfocused.core.ui.v5.charts.UsageRow
import com.dculus.stayfocused.core.ui.v5.components.AppIconTile
import com.dculus.stayfocused.core.ui.v5.components.DotMatrixText
import com.dculus.stayfocused.core.ui.v5.components.GlowCard
import com.dculus.stayfocused.core.ui.v5.components.LocalTabBarPadding
import com.dculus.stayfocused.core.ui.v5.components.RoundIconButton
import com.dculus.stayfocused.core.ui.v5.components.SegmentedPill
import com.dculus.stayfocused.core.ui.v5.components.SegmentedPillSize
import com.dculus.stayfocused.core.ui.v5.components.V5Card
import com.dculus.stayfocused.core.ui.v5.icons.V5Icons
import com.dculus.stayfocused.core.ui.v5.theme.V5Gradients
import com.dculus.stayfocused.core.ui.v5.theme.V5Screen
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme
import com.dculus.stayfocused.core.ui.v5.theme.v5StatusBarPadding

@Composable
internal fun InsightsRoute(viewModel: InsightsViewModel = hiltViewModel()) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    InsightsScreen(
        state = state,
        onEvent = { event ->
            when (event) {
                is InsightsEvent.SelectRange -> viewModel.selectRange(event.range)
                InsightsEvent.PreviousDay -> viewModel.previousDay()
                InsightsEvent.NextDay -> viewModel.nextDay()
                is InsightsEvent.SelectMetric -> viewModel.selectMetric(event.metric)
            }
        },
    )
}

private val Ranges = InsightsRange.entries
private val Metrics = InsightsMetric.entries

@Composable
internal fun InsightsScreen(
    state: InsightsUiState,
    onEvent: (InsightsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    V5Screen(darkContent = false, modifier = modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .v5StatusBarPadding(12.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 14.dp, end = 14.dp, bottom = 24.dp + LocalTabBarPadding.current),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(state.range) { onEvent(InsightsEvent.SelectRange(it)) }
            if (state.range == InsightsRange.Day) {
                DayStepper(state, { onEvent(InsightsEvent.PreviousDay) }, { onEvent(InsightsEvent.NextDay) })
            }
            val day = state.day
            when {
                state.loading -> Unit
                day == null -> EmptyDay()
                else -> HeroCard(day, isToday = state.dayOffset == 0)
            }
            if (!state.loading) Tiles(state.unlocksToday, state.blockedToday)
            if (day != null) {
                AppList(day, state.metric) { onEvent(InsightsEvent.SelectMetric(it)) }
            }
        }
    }
}

@Composable
private fun Header(
    range: InsightsRange,
    onSelect: (InsightsRange) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.insights_title),
            style = V5Theme.type.title,
            color = V5Theme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        SegmentedPill(
            options = listOf(stringResource(R.string.insights_range_day), stringResource(R.string.insights_range_week)),
            selectedIndex = Ranges.indexOf(range),
            onSelect = { onSelect(Ranges[it]) },
            modifier = Modifier.width(150.dp),
            size = SegmentedPillSize.Small,
        )
    }
}

/** Previous/next day with the weekday and date between. */
@Composable
private fun DayStepper(
    state: InsightsUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
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
        RoundIconButton(
            V5Icons.back,
            stringResource(R.string.insights_previous_day),
            onPrevious,
            enabled = state.canStepBack,
        )
        Column(
            Modifier.semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = V5Theme.type.label, color = V5Theme.colors.ink)
            Text(dayDateLabel(state.date), style = V5Theme.type.body, color = V5Theme.colors.textSecondary)
        }
        RoundIconButton(
            V5Icons.arrow,
            stringResource(R.string.insights_next_day),
            onNext,
            enabled = state.canStepForward,
        )
    }
}

@Composable
private fun HeroCard(
    day: InsightsDay,
    isToday: Boolean,
) {
    val colors = V5Theme.colors
    val label =
        when {
            day.range == InsightsRange.Week -> stringResource(R.string.insights_label_week)
            isToday -> stringResource(R.string.insights_label_today)
            else -> stringResource(R.string.insights_metric_screen_time)
        }
    val (hours, minutes) = dayHeroParts(day.heroMins)
    val unit = V5Theme.type.rule.copy(fontSize = 22.sp, fontWeight = FontWeight.Light)
    val unitColor = colors.onGradient.copy(alpha = 0.8f)
    val averageLabel =
        if (day.range == InsightsRange.Day) {
            stringResource(R.string.insights_avg_per_hour, Math.round(day.average))
        } else {
            formatMinutes(Math.round(day.average))
        }
    val heroDescription = stringResource(R.string.insights_hero_description, label, formatMinutes(day.heroMins))
    GlowCard(V5Gradients.violet, Modifier.fillMaxWidth(), radius = 34.dp) {
        Column(
            Modifier.clearAndSetSemantics { contentDescription = heroDescription },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, style = V5Theme.type.label.copy(fontSize = 15.sp), color = colors.onGradient)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    hours,
                    style = V5Theme.type.displayL,
                    color = colors.onGradient,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    stringResource(R.string.insights_unit_hours),
                    style = unit,
                    color = unitColor,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    minutes,
                    style = V5Theme.type.displayL,
                    color = colors.onGradient,
                    modifier = Modifier.padding(start = 6.dp).alignByBaseline(),
                )
                Text(
                    stringResource(R.string.insights_unit_minutes),
                    style = unit,
                    color = unitColor,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        Column(Modifier.padding(top = 14.dp)) {
            val axis =
                day.axisLabels.ifEmpty {
                    listOf(
                        stringResource(R.string.insights_axis_12am),
                        stringResource(R.string.insights_axis_6am),
                        stringResource(R.string.insights_axis_12pm),
                        stringResource(R.string.insights_axis_6pm),
                        stringResource(R.string.insights_axis_11pm),
                    )
                }
            val chartDescription =
                stringResource(
                    if (day.range == InsightsRange.Day) R.string.insights_chart_day else R.string.insights_chart_week,
                    averageLabel,
                )
            GlassBarChart(
                values = day.values,
                max = day.chartMax,
                average = day.average,
                averageLabel = averageLabel,
                axisLabels = axis,
                contentDescription = chartDescription,
                highlightIndex = day.highlightIndex,
                mode = day.mode,
            )
        }
    }
}

@Composable
private fun Tiles(
    unlocks: Int,
    blocked: Int,
) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CountTile(
            title = stringResource(R.string.insights_unlocks),
            count = unlocks,
            caption = stringResource(R.string.insights_today),
            description = stringResource(R.string.insights_unlocks_description, unlocks),
            gradient = V5Gradients.orangeTile,
            dotSize = 6.dp,
            modifier = Modifier.weight(1f),
        )
        CountTile(
            title = stringResource(R.string.insights_blocked),
            count = blocked,
            caption = stringResource(R.string.insights_times_today),
            description = stringResource(R.string.insights_blocked_description, blocked),
            gradient = V5Gradients.violet,
            dotSize = 5.dp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CountTile(
    title: String,
    count: Int,
    caption: String,
    description: String,
    gradient: com.dculus.stayfocused.core.ui.v5.theme.V5Gradient,
    dotSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val white = V5Theme.colors.onGradient
    GlowCard(
        gradient,
        modifier.fillMaxHeight().clearAndSetSemantics { contentDescription = description },
        radius = 32.dp,
        padding = 16.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(title, style = V5Theme.type.label.copy(fontSize = 15.sp), color = white)
            DotMatrixText(count.toString(), description, dotSize, white)
            Text(caption, style = V5Theme.type.label.copy(fontSize = 13.sp), color = white)
        }
    }
}

@Composable
private fun AppList(
    day: InsightsDay,
    metric: InsightsMetric,
    onMetric: (InsightsMetric) -> Unit,
) {
    V5Card(Modifier.fillMaxWidth(), radius = 34.dp, padding = 0.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            SegmentedPill(
                options =
                    listOf(
                        stringResource(R.string.insights_metric_screen_time),
                        stringResource(R.string.insights_metric_opens),
                    ),
                selectedIndex = Metrics.indexOf(metric),
                onSelect = { onMetric(Metrics[it]) },
                modifier = Modifier.padding(vertical = 6.dp),
                size = SegmentedPillSize.Small,
            )
            if (day.apps.isEmpty()) {
                Text(
                    stringResource(R.string.insights_no_apps),
                    style = V5Theme.type.body,
                    color = V5Theme.colors.textSecondary,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }
            day.apps.forEach { app ->
                UsageRow(
                    icon = { AppIconTile(app.pkg, 38.dp, label = app.label) },
                    name = app.label,
                    timeText =
                        if (metric == InsightsMetric.ScreenTime) {
                            formatMinutes(app.value)
                        } else {
                            pluralStringResource(R.plurals.insights_opens_count, app.value, app.value)
                        },
                    fraction = app.fraction,
                )
            }
        }
    }
}

@Composable
private fun EmptyDay() {
    V5Card(Modifier.fillMaxWidth(), radius = 34.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.insights_empty_title),
                style = V5Theme.type.bodyL,
                color = V5Theme.colors.ink,
            )
            Text(
                stringResource(R.string.insights_empty_body),
                style = V5Theme.type.body,
                color = V5Theme.colors.textSecondary,
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, locale = "en")
@Composable
internal fun InsightsPreview() {
    InsightsScreen(previewInsightsState(InsightsRange.Day), {})
}

@Preview(widthDp = 390, heightDp = 844, locale = "en")
@Composable
internal fun InsightsWeekPreview() {
    InsightsScreen(previewInsightsState(InsightsRange.Week), {})
}
