package com.dculus.stayfocused.feature.insights

import com.dculus.stayfocused.core.model.LedColumn
import com.dculus.stayfocused.core.model.ledLevels
import com.dculus.stayfocused.core.usage.AppUsageStat
import com.dculus.stayfocused.core.usage.DayUsageStats
import com.dculus.stayfocused.core.usage.UsageAverages
import com.dculus.stayfocused.core.usage.totalOpens
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** How far back the stepper goes: today (0) to -6. */
internal const val OLDEST_DAY_OFFSET = -6

internal const val LED_ROWS = 10
internal const val HOURS_PER_DAY = 24
private const val MILLIS_PER_SECOND = 1000L

internal enum class InsightsMetric { ScreenTime, Opens, Unlocks }

/** Title of the day stepper; the weekday name is locale-formatted, the other two are string resources. */
internal sealed interface DayTitle {
    data object Today : DayTitle

    data object Yesterday : DayTitle

    data class Weekday(
        val name: String,
    ) : DayTitle
}

internal fun dayTitle(
    offset: Int,
    date: LocalDate,
    locale: Locale = Locale.getDefault(),
): DayTitle =
    when (offset) {
        0 -> DayTitle.Today
        -1 -> DayTitle.Yesterday
        else -> DayTitle.Weekday(date.dayOfWeek.getDisplayName(TextStyle.FULL, locale))
    }

/** "06 OCT". */
internal fun dayDateLabel(
    date: LocalDate,
    locale: Locale = Locale.getDefault(),
): String = DateTimeFormatter.ofPattern("dd MMM", locale).format(date).uppercase(locale)

internal fun canStepBack(offset: Int): Boolean = offset > OLDEST_DAY_OFFSET

internal fun canStepForward(offset: Int): Boolean = offset < 0

/** One row of the ranked list. [value] is minutes for screen time, a count otherwise. */
internal data class RankedApp(
    val rank: Int,
    val pkg: String,
    val label: String,
    val value: Int,
    /** 0..1 relative to the top app. */
    val fraction: Float,
) {
    /** "01", "02", ... */
    val rankLabel: String get() = rank.toString().padStart(2, '0')
}

private fun AppUsageStat.valueFor(metric: InsightsMetric): Int =
    when (metric) {
        // Sessions under 30 s round to 0 min but are still real use: show them as 1 min.
        InsightsMetric.ScreenTime -> if (millis > 0) maxOf(mins, 1) else 0

        InsightsMetric.Opens -> opens

        // Unlocks are ranked by the app opened first after an unlock (M3-01).
        InsightsMetric.Unlocks -> firstAfterUnlock
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

/** Everything the content composable draws for one day and metric. */
internal data class InsightsDay(
    val metric: InsightsMetric,
    /** Minutes for [InsightsMetric.ScreenTime], otherwise a count. */
    val total: Int,
    /** The average to print after "AVG", or null when there is no history yet. */
    val average: Int?,
    val levels: List<LedColumn>,
    val avgFraction: Float?,
    val apps: List<RankedApp>,
)

private fun DayUsageStats.totalFor(metric: InsightsMetric): Int =
    when (metric) {
        InsightsMetric.ScreenTime -> totalMins
        InsightsMetric.Opens -> totalOpens
        InsightsMetric.Unlocks -> unlocks
    }

private fun UsageAverages.averageFor(metric: InsightsMetric): Int? =
    when {
        !hasData -> null
        metric == InsightsMetric.ScreenTime -> avgTotalMins
        metric == InsightsMetric.Opens -> avgOpens
        else -> avgUnlocks
    }

/** Average per chart hour, in the chart's unit (seconds, or unlocks). */
private fun UsageAverages.avgPerHour(unlockChart: Boolean): Double? =
    when {
        !hasData -> null
        unlockChart -> avgUnlocks.toDouble() / HOURS_PER_DAY
        else -> avgHourlyMillis.toDouble() / MILLIS_PER_SECOND
    }

/** [InsightsMetric.ScreenTime] and [Opens] chart the hourly foreground time; [Unlocks] the hourly unlocks. */
internal fun buildInsightsDay(
    stats: DayUsageStats,
    averages: UsageAverages,
    metric: InsightsMetric,
    currentHour: Int?,
    labelOf: (String) -> String,
): InsightsDay {
    val unlockChart = metric == InsightsMetric.Unlocks
    val hourly =
        if (unlockChart) stats.hourlyUnlocks else stats.hourlyMillis.map { (it / MILLIS_PER_SECOND).toInt() }
    val avgPerHour = averages.avgPerHour(unlockChart)
    val max = hourly.maxOrNull() ?: 0
    return InsightsDay(
        metric = metric,
        total = stats.totalFor(metric),
        average = averages.averageFor(metric),
        levels = ledLevels(hourly, LED_ROWS, currentHour),
        avgFraction = if (avgPerHour != null && max > 0) (avgPerHour / max).toFloat().coerceIn(0f, 1f) else null,
        apps = rankApps(stats.apps, metric, labelOf),
    )
}

/** A day with nothing recorded gets the empty state instead of zeros everywhere. */
internal fun DayUsageStats?.hasNoData(): Boolean = this == null || (totalMillis <= 0L && unlocks <= 0 && apps.isEmpty())
