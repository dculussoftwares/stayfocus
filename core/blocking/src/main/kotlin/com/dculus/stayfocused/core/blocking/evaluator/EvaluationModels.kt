package com.dculus.stayfocused.core.blocking.evaluator

import com.dculus.stayfocused.core.model.Block
import com.dculus.stayfocused.core.model.BreakSession
import com.dculus.stayfocused.core.model.FocusSession
import com.dculus.stayfocused.core.model.LockedApp
import com.dculus.stayfocused.core.model.TemporaryAllowance
import java.time.Instant
import java.time.ZonedDateTime

/** Foreground time of one app, already clipped to the current day / clock hour. */
data class AppUsageSnapshot(
    val todayMs: Long,
    val thisHourMs: Long,
)

/** Key of a cycle state: one per (block, app). */
data class CycleKey(
    val blockId: String,
    val pkg: String,
)

/**
 * Progress of a "use, then rest" cycle. [usedMs] is foreground time in the current window;
 * [lockedUntil] is set while the app is resting.
 */
data class CycleState(
    val usedMs: Long = 0,
    val lockedUntil: Instant? = null,
)

enum class BlockReason { BREAK, FOCUS, NOW, LIMIT_DAILY, LIMIT_HOURLY, CYCLE_REST, SCHEDULE, MANUAL_LOCK }

sealed interface Decision {
    data object Allow : Decision

    /** [until] is null when the block has no end (manual lock). */
    data class Block(
        val reason: BlockReason,
        val blockId: String?,
        val until: Instant?,
    ) : Decision
}

/** Everything the evaluator needs to decide about [pkg] at [now]. */
data class EvaluationContext(
    val now: ZonedDateTime,
    val pkg: String,
    val blocks: List<Block>,
    val lockedApps: List<LockedApp> = emptyList(),
    val breakSession: BreakSession? = null,
    val focusSession: FocusSession? = null,
    val allowances: List<TemporaryAllowance> = emptyList(),
    val usage: Map<String, AppUsageSnapshot> = emptyMap(),
    val cycleStates: Map<CycleKey, CycleState> = emptyMap(),
    val allowlist: Set<String> = emptySet(),
)
