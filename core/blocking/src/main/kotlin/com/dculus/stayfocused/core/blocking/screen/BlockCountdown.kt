package com.dculus.stayfocused.core.blocking.screen

import com.dculus.stayfocused.core.blocking.evaluator.BlockReason
import com.dculus.stayfocused.core.blocking.evaluator.Decision
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val MS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L

/** Longest span shown as `m:ss`; mmss rounds up, so anything above 59:59 would print "60:00". */
private const val MMSS_MAX_MS = (SECONDS_PER_HOUR - 1) * MS_PER_SECOND

/** `m:ss`, rounding up so the display never shows 0:00 while time is left (prototype `mmss`). */
fun mmss(ms: Long): String {
    val s = maxOf(0L, (ms + MS_PER_SECOND - 1) / MS_PER_SECOND)
    return "${s / SECONDS_PER_MINUTE}:${(s % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"
}

/** `hh:mm:ss` (prototype `hms`). */
fun hms(ms: Long): String {
    val s = maxOf(0L, ms / MS_PER_SECOND)
    return listOf(s / SECONDS_PER_HOUR, s / SECONDS_PER_MINUTE % SECONDS_PER_MINUTE, s % SECONDS_PER_MINUTE)
        .joinToString(":") { it.toString().padStart(2, '0') }
}

/** What the block screen shows under the reason line. */
sealed interface BlockTimeLine {
    /** Manual lock without an end: nothing to show. */
    data object None : BlockTimeLine

    /** Live countdown ("OPENS IN 4:59"). */
    data class Countdown(
        val text: String,
    ) : BlockTimeLine

    /** Fixed clock time ("UNTIL 18:30"). */
    data class Until(
        val clock: String,
    ) : BlockTimeLine
}

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Time line for [decision] at [now]. Reasons that end after a known span count down (`mmss` under an hour,
 * `hms` above); schedules, "now" blocks and timed manual locks show the end as a clock time; an open-ended
 * manual lock shows nothing.
 */
fun timeLineFor(
    decision: Decision.Block,
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): BlockTimeLine {
    val until = decision.until ?: return BlockTimeLine.None
    return when (decision.reason) {
        BlockReason.SCHEDULE, BlockReason.NOW, BlockReason.MANUAL_LOCK -> {
            BlockTimeLine.Until(clockFormat.format(until.atZone(zone)))
        }

        else -> {
            val left = maxOf(0L, until.toEpochMilli() - now.toEpochMilli())
            BlockTimeLine.Countdown(if (left <= MMSS_MAX_MS) mmss(left) else hms(left))
        }
    }
}
