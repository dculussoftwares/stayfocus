package com.dculus.stayfocused.feature.insights

import com.dculus.stayfocused.core.ui.v5.charts.GlassBarMode
import com.dculus.stayfocused.core.usage.AppUsageStat
import com.dculus.stayfocused.core.usage.DayUsageStats
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

internal const val HOURS_PER_DAY = 24
internal const val WEEK_DAYS = 7
internal const val TOP_APPS = 6

/** The prototype's fixed chart scales (20 min per hour, 4 h per day); a busier hour or day stretches the scale. */
internal const val DAY_CHART_MIN_MAX = 20
internal const val WEEK_CHART_MIN_MAX = 240

internal enum class InsightsRange { Day, Week }

/** What the ranked list shows. [Opens] is a v5-extra (M13-04). */
internal enum class InsightsMetric { ScreenTime, Opens }

/** One row of the ranked list. [value] is minutes for screen time, a count otherwise. */
internal data class RankedApp(
    val rank: Int,
    val pkg: String,
    val label: String,
    val value: Int,
    /** 0..1 relative to the top app. */
    val fraction: Float,
)

private fun AppUsageStat.valueFor(metric: InsightsMetric): Int =
    when (metric) {
        // Sessions under 30 s round to 0 min but are still real use: show them as 1 min.
        InsightsMetric.ScreenTime -> if (millis > 0) maxOf(mins, 1) else 0

        InsightsMetric.Opens -> opens
    }

/** Apps sorted by [metric] (desc), ties broken by screen time then label; apps with a value of 0 are dropped. */
internal fun rankApps(
    apps: List<AppUsageStat>,
    metric: InsightsMetric,
    labelOf: (String) -> String,
): List<RankedApp> {
    val ranked =
        apps
            .map { it to it.valueFor(metric) }
            .filter { (_, value) -> value > 0 }
            .sortedWith(
                compareByDescending<Pair<AppUsageStat, Int>> { it.second }
                    .thenByDescending { it.first.millis }
                    .thenBy { labelOf(it.first.pkg).lowercase() },
            )
    val top = ranked.firstOrNull()?.second ?: return emptyList()
    return ranked.mapIndexed { i, (app, value) ->
        RankedApp(i + 1, app.pkg, labelOf(app.pkg), value, value.toFloat() / top)
    }
}

/** Everything the content composables draw for one day or week. */
internal data class InsightsDay(
    val range: InsightsRange,
    /** Screen time in minutes: the day's total, or the daily average of the week. */
    val heroMins: Int,
    /** Bar values in minutes: 24 hours, or 7 days ending today. */
    val values: List<Int>,
    val chartMax: Int,
    /** Dashed average line, in the unit of [values]: minutes per hour (day) or per day (week). */
    val average: Float,
    val highlightIndex: Int?,
    /** Week: the weekday letters. Day: empty, the screen draws its hour labels from resources. */
    val axisLabels: List<String>,
    val apps: List<RankedApp>,
) {
    val mode: GlassBarMode get() = if (range == InsightsRange.Day) GlassBarMode.Day else GlassBarMode.Week
}

internal fun dayHeroParts(mins: Int): Pair<String, String> =
    (mins / 60).toString() to (mins % 60).toString().padStart(2, '0')

/** One day: hourly minutes, average per hour = total / 24, the current hour highlighted when it is today. */
internal fun buildDay(
    stats: DayUsageStats,
    metric: InsightsMetric,
    currentHour: Int?,
    labelOf: (String) -> String,
): InsightsDay {
    val values = stats.hourlyMins
    return InsightsDay(
        range = InsightsRange.Day,
        heroMins = stats.totalMins,
        values = values,
        chartMax = maxOf(DAY_CHART_MIN_MAX, values.maxOrNull() ?: 0),
        average = stats.totalMins.toFloat() / HOURS_PER_DAY,
        highlightIndex = currentHour,
        axisLabels = emptyList(),
        apps = rankApps(stats.apps, metric, labelOf).take(TOP_APPS),
    )
}

/** Sums the apps of several days into one list (screen time, opens, unlock starts). */
internal fun aggregateApps(days: List<DayUsageStats?>): List<AppUsageStat> =
    days
        .filterNotNull()
        .flatMap { it.apps }
        .groupBy { it.pkg }
        .map { (pkg, list) ->
            AppUsageStat(pkg, list.sumOf { it.millis }, list.sumOf { it.opens }, list.sumOf { it.firstAfterUnlock })
        }

/**
 * The last 7 days, oldest first with today last ([days] has [WEEK_DAYS] entries, null for days not cached): bar =
 * the day's minutes, hero and average line = the mean of the 7 days, apps = 7-day totals.
 */
internal fun buildWeek(
    days: List<DayUsageStats?>,
    metric: InsightsMetric,
    axisLabels: List<String>,
    labelOf: (String) -> String,
): InsightsDay {
    val values = days.map { it?.totalMins ?: 0 }
    val average = values.sum().toFloat() / WEEK_DAYS
    return InsightsDay(
        range = InsightsRange.Week,
        heroMins = Math.round(average),
        values = values,
        chartMax = maxOf(WEEK_CHART_MIN_MAX, values.maxOrNull() ?: 0),
        average = average,
        highlightIndex = days.lastIndex,
        axisLabels = axisLabels,
        apps = rankApps(aggregateApps(days), metric, labelOf).take(TOP_APPS),
    )
}

/** Narrow weekday letters of the 7 days ending [today] ("M T W T F S S" starts at the right weekday). */
internal fun weekAxisLabels(
    today: LocalDate,
    locale: Locale = Locale.getDefault(),
): List<String> =
    (WEEK_DAYS - 1 downTo 0).map { today.minusDays(it.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, locale) }

/** A day with nothing recorded gets the empty state instead of zeros everywhere. */
internal fun DayUsageStats?.hasNoData(): Boolean = this == null || (totalMillis <= 0L && unlocks <= 0 && apps.isEmpty())
