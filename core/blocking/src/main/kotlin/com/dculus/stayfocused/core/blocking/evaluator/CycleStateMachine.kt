package com.dculus.stayfocused.core.blocking.evaluator

import com.dculus.stayfocused.core.model.Block
import java.time.Instant

/** "Use, then rest": counts foreground time only; the next open after the rest starts a fresh window. */
@Suppress("ReturnCount") // guard clauses
object CycleStateMachine {
    private const val MS_PER_MIN = 60_000L

    /** Adds [foregroundDeltaMs] of foreground time at [now]. While resting the delta is ignored. */
    fun advance(
        state: CycleState,
        foregroundDeltaMs: Long,
        now: Instant,
        block: Block,
    ): CycleState {
        val lockedUntil = state.lockedUntil
        if (lockedUntil != null && lockedUntil > now) return state
        val useMs = (block.useMins ?: return CycleState()) * MS_PER_MIN
        val restMs = (block.restMins ?: 0) * MS_PER_MIN
        val used = state.usedMs + foregroundDeltaMs.coerceAtLeast(0)
        return if (used >= useMs) {
            CycleState(usedMs = 0, lockedUntil = now.plusMillis(restMs))
        } else {
            CycleState(usedMs = used, lockedUntil = null)
        }
    }
}
