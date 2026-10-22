package com.dculus.stayfocused.feature.block

import com.dculus.stayfocused.core.model.Block
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.DaysOfWeek
import com.dculus.stayfocused.core.model.LimitPeriod
import java.time.LocalTime
import java.time.ZoneId

/** Usage of a limit or cycle block against its allowance, in minutes (the tick meter and the used/max text). */
data class BlockProgress(
    val usedMins: Int,
    val maxMins: Int,
) {
    /** `used / max`, 0 when there is no allowance. The meter clamps it to 0..1. */
    val fraction: Float get() = if (maxMins > 0) usedMins.toFloat() / maxMins else 0f
}

/**
 * Today's usage of a block's apps against its allowance, from the shipped usage stats (V5_SPEC §5):
 * a daily limit counts all of today's minutes of its apps; a cycle counts them up to its "use" minutes (the open
 * window). Hourly limits, schedules and "block now" have no meter. [todayMins] maps package to minutes used today.
 */
fun blockProgress(
    block: Block,
    todayMins: Map<String, Int>,
): BlockProgress? {
    val used = block.apps.sumOf { todayMins[it] ?: 0 }
    return when (block.type) {
        BlockType.LIMIT -> {
            block.limitMins?.takeIf { block.period != LimitPeriod.HOURLY }?.let { BlockProgress(used, it) }
        }

        BlockType.CYCLE -> {
            block.useMins?.let { BlockProgress(minOf(used, it), it) }
        }

        BlockType.SCHEDULE, BlockType.NOW -> {
            null
        }
    }
}

/** The "Until HH:mm" time of a "block now" block in [zone]; null when it has not started. */
fun nowBlockEnds(
    block: Block,
    zone: ZoneId,
): LocalTime? {
    val started = block.startedAt
    val mins = block.durationMins
    return if (started == null || mins == null) {
        null
    } else {
        started
            .plusSeconds(mins * SECONDS_PER_MINUTE)
            .atZone(zone)
            .toLocalTime()
            .withSecond(0)
            .withNano(0)
    }
}

/** How a block's [DaysOfWeek] reads in a rule sentence: "every day", "Mon–Fri", "weekends", else a list. */
enum class DaysStyle { EVERY_DAY, WEEKDAYS, WEEKENDS, NONE, LIST }

fun daysStyle(days: DaysOfWeek): DaysStyle =
    when {
        days == DaysOfWeek.ALL -> DaysStyle.EVERY_DAY
        days == DaysOfWeek.WEEKDAYS -> DaysStyle.WEEKDAYS
        days == DaysOfWeek.WEEKENDS -> DaysStyle.WEEKENDS
        days.isEmpty -> DaysStyle.NONE
        else -> DaysStyle.LIST
    }

/** Title of a block card: the first two app names joined " + ", then " +{n}" for the rest. */
fun blockTitle(appLabels: List<String>): String {
    val head = appLabels.take(TITLE_APPS).joinToString(" + ")
    val more = appLabels.size - TITLE_APPS
    return if (more > 0) "$head +$more" else head
}

private const val SECONDS_PER_MINUTE = 60L
private const val TITLE_APPS = 2
