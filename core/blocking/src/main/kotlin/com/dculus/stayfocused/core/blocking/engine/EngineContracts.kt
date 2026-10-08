package com.dculus.stayfocused.core.blocking.engine

import com.dculus.stayfocused.core.blocking.evaluator.AppUsageSnapshot
import com.dculus.stayfocused.core.blocking.evaluator.CycleKey
import com.dculus.stayfocused.core.blocking.evaluator.CycleState
import com.dculus.stayfocused.core.model.TemporaryAllowance
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZonedDateTime

/** Foreground time of apps today and in the current clock hour. */
interface AppUsageProvider {
    suspend fun usage(
        pkgs: Set<String>,
        now: ZonedDateTime,
    ): Map<String, AppUsageSnapshot>
}

/** Receives the foreground intervals the engine measures. */
fun interface ForegroundTimeRecorder {
    fun record(
        pkg: String,
        from: Instant,
        to: Instant,
    )
}

/** Foreground time the engine itself has measured, for the part UsageStats has not caught up with yet. */
fun interface LiveForegroundSource {
    /** Milliseconds [pkg] was in front between [from] and [to]. */
    fun foregroundMs(
        pkg: String,
        from: Instant,
        to: Instant,
    ): Long
}

/** Packages that may never be blocked: the user must always be able to call, text, change settings and go home. */
fun interface BlockAllowlistProvider {
    fun allowlist(): Set<String>
}

/** Active temporary allowances ("unlock for N minutes"). */
fun interface TemporaryAllowanceSource {
    fun observe(): Flow<List<TemporaryAllowance>>
}

/** Persistence of cycle ("use, then rest") progress. */
interface CycleStateStore {
    suspend fun load(key: CycleKey): CycleState?

    suspend fun save(
        key: CycleKey,
        state: CycleState,
        now: Instant,
    )
}

/** Records every block the engine enforces (feeds the "blocked" stat). */
fun interface BlockEventLog {
    suspend fun record(
        pkg: String,
        blockId: String?,
        reason: String,
        at: Instant,
    )
}

/** Tells the user that a break has run out ("Break over"). Must not throw for a missing permission. */
fun interface BreakEndNotifier {
    fun breakEnded()
}
