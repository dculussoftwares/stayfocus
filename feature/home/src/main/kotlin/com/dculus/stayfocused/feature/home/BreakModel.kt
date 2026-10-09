package com.dculus.stayfocused.feature.home

import java.util.Locale
import kotlin.math.ceil

internal const val BREAK_SEGMENTS = 30
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L

/** What the Home break card shows. */
internal sealed interface BreakUi {
    /** No break running: the lime "Take a break" card. */
    data object Idle : BreakUi

    /** A break is running with [remainingMs] left of [totalMs]. */
    data class Active(
        val remainingMs: Long,
        val totalMs: Long,
    ) : BreakUi {
        val countdown: String get() = mmss(remainingMs)
        val elapsedSegments: Int get() = elapsedSegments(remainingMs, totalMs)
    }
}

/** Prototype `mmss`: whole seconds rounded up, `m:ss` (minutes are not capped at 59). */
internal fun mmss(ms: Long): String {
    val seconds = ceil(ms.coerceAtLeast(0L) / MILLIS_PER_SECOND.toDouble()).toLong()
    return String.format(Locale.ROOT, "%d:%02d", seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
}

/**
 * Segments of the 30-segment bar that are already used up (drawn dim). Mirrors the prototype's
 * `i / 30 < done`, i.e. `ceil(done * 30)` segments for `done` = elapsed fraction.
 */
internal fun elapsedSegments(
    remainingMs: Long,
    totalMs: Long,
): Int {
    if (totalMs <= 0L) return BREAK_SEGMENTS
    val done = 1.0 - remainingMs.coerceIn(0L, totalMs).toDouble() / totalMs
    return ceil(done * BREAK_SEGMENTS).toInt().coerceIn(0, BREAK_SEGMENTS)
}

/** Milliseconds until the countdown text changes, so the card redraws once per displayed second. */
internal fun millisToNextTick(remainingMs: Long): Long = ((remainingMs - 1L).coerceAtLeast(0L) % MILLIS_PER_SECOND) + 1L
