package com.dculus.stayfocused.core.blocking.engine

import android.util.Log
import com.dculus.stayfocused.core.blocking.ForegroundAppTracker
import com.dculus.stayfocused.core.blocking.evaluator.BlockReason
import com.dculus.stayfocused.core.blocking.evaluator.CycleKey
import com.dculus.stayfocused.core.blocking.evaluator.CycleState
import com.dculus.stayfocused.core.blocking.evaluator.CycleStateMachine
import com.dculus.stayfocused.core.blocking.evaluator.Decision
import com.dculus.stayfocused.core.blocking.evaluator.EvaluationContext
import com.dculus.stayfocused.core.blocking.evaluator.RuleEvaluator
import com.dculus.stayfocused.core.blocking.screen.BlockPresenter
import com.dculus.stayfocused.core.data.repository.BlockRepository
import com.dculus.stayfocused.core.data.repository.BreakRepository
import com.dculus.stayfocused.core.data.repository.LockedAppsRepository
import com.dculus.stayfocused.core.data.repository.SettingsRepository
import com.dculus.stayfocused.core.model.Block
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.BreakSession
import com.dculus.stayfocused.core.model.FocusSession
import com.dculus.stayfocused.core.model.LockedApp
import com.dculus.stayfocused.core.model.TemporaryAllowance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Everything besides the foreground app and the clock that a decision depends on. */
internal data class EngineInputs(
    val blocks: List<Block>,
    val lockedApps: List<LockedApp>,
    val breakSession: BreakSession?,
    val focusSession: FocusSession?,
    val allowances: List<TemporaryAllowance>,
)

/**
 * Enforces the blocks: watches the foreground app, evaluates [RuleEvaluator] on every change and again when the
 * next decision may change (at least every [MAX_IDLE_MS] while a tracked app is in front), shows the block
 * screen, logs the block and feeds foreground time to the usage provider and the cycle state machine.
 *
 * Lives exactly as long as the accessibility service: [start] on connect, [stop] on unbind/destroy. A restart waits
 * for the previous run to finish (including its final bookings), so two runs never touch the state at once.
 */
@Singleton
@Suppress("LongParameterList", "TooManyFunctions") // one collaborator per input; each function is small
class BlockingEngine
    @Inject
    internal constructor(
        private val tracker: ForegroundAppTracker,
        private val blocks: BlockRepository,
        private val lockedApps: LockedAppsRepository,
        private val breaks: BreakRepository,
        private val settings: SettingsRepository,
        private val allowances: TemporaryAllowanceSource,
        private val cycleStore: CycleStateStore,
        private val events: BlockEventLog,
        private val usage: AppUsageProvider,
        private val recorder: ForegroundTimeRecorder,
        private val allowlist: BlockAllowlistProvider,
        private val presenter: BlockPresenter,
        private val breakEnd: BreakEndNotifier,
        private val clock: Clock,
        private val monotonic: MonotonicClock,
        @EngineDispatcher private val dispatcher: CoroutineDispatcher,
    ) {
        /** An uninterrupted stay of [pkg] in front. [counting] is false while it is being blocked. */
        private class Session(
            val pkg: String,
            var flushedAt: Instant,
            var flushedMono: Long,
            var counting: Boolean = true,
        )

        private data class Shown(
            val session: Session,
            val reason: BlockReason,
            val blockId: String?,
            val at: Instant,
        )

        private val lock = Any()
        private var scope: CoroutineScope? = null
        private var shutdown: Job? = null
        private val shutdownScope = CoroutineScope(SupervisorJob() + dispatcher)

        @Volatile private var running = false

        // Written by one run at a time (see start/stop); the previous run is joined before the next one starts.
        private var session: Session? = null
        private var shown: Shown? = null

        /** The inputs the time since the last evaluation has to be booked against. */
        private var bookingInputs: EngineInputs? = null
        private val cycleCache = ConcurrentHashMap<CycleKey, CycleState>()

        /** Cycle states whose last save failed; they are saved again at the next opportunity, even if unchanged. */
        private val unsaved = ConcurrentHashMap.newKeySet<CycleKey>()

        @Volatile private var latestInputs: EngineInputs? = null

        /** End of the break the engine is waiting to announce; survives a restart of the run, not of the engine. */
        @Volatile private var watchedBreakEnd: Instant? = null

        // Only touched by the (single) collecting coroutine of a run.
        private var handledClockTick = 0

        @Volatile private var announcedBreak: BreakSession? = null

        @Volatile private var cachedAllowlist: Set<String>? = null

        /** Bumped when the wall clock or time zone changes, so pending timers are re-computed. */
        private val clockChanges = MutableStateFlow(0)

        /**
         * The wall clock or the time zone was changed (`ACTION_TIME_CHANGED` / `ACTION_TIMEZONE_CHANGED`): every
         * schedule boundary moved, so the decision is evaluated again right away instead of at the old wake-up.
         */
        fun onClockChanged() {
            clockChanges.update { it + 1 }
        }

        fun start() {
            synchronized(lock) {
                if (scope != null) return
                val s = CoroutineScope(SupervisorJob() + dispatcher)
                scope = s
                running = true
                handledClockTick = clockChanges.value
                val previous = shutdown
                s.launch {
                    previous?.join()
                    runWithRetry()
                }
            }
        }

        /** Cancels everything the engine started. Safe to call repeatedly. */
        fun stop() {
            val stoppedAt = clock.instant()
            synchronized(lock) {
                val s = scope ?: return
                scope = null
                running = false
                val old = s.coroutineContext[Job]
                s.cancel()
                // Started right here, under the lock, so a run that starts next always waits for a running job.
                shutdown =
                    shutdownScope.launch {
                        old?.join()
                        finish(stoppedAt)
                    }
            }
            presenter.dismissOverlay()
        }

        /** Books the time since the last evaluation, then forgets everything about the run. */
        private suspend fun finish(stoppedAt: Instant) {
            try {
                val s = session
                val inputs = bookingInputs
                if (s != null && s.counting && inputs != null) {
                    // Up to the moment of stop(), not however long the old run took to wind down.
                    if (stoppedAt > s.flushedAt) {
                        withContext(NonCancellable) { bookForeground(s.pkg, s.flushedAt, stoppedAt, inputs, inputs) }
                    }
                }
                retryUnsavedCycles(stoppedAt)
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                Log.e(TAG, "Final booking failed", e)
            } finally {
                session = null
                shown = null
                bookingInputs = null
                latestInputs = null
                watchedBreakEnd = null
                announcedBreak = null
                cachedAllowlist = null
                cycleCache.clear()
                unsaved.clear()
            }
        }

        private suspend fun retryUnsavedCycles(now: Instant) {
            for (key in unsaved.toList()) {
                val state = cycleCache[key] ?: continue
                try {
                    cycleStore.save(key, state, now)
                    unsaved.remove(key)
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    Log.e(TAG, "Could not persist cycle state of ${key.blockId}", e)
                }
            }
        }

        /**
         * Re-evaluates [pkg] at [now] with the most recent inputs; used by the block screen so it ends exactly when
         * the rule says so. Allow once the engine has stopped (nothing enforces the block any more); null while it
         * has not seen its inputs yet.
         */
        internal suspend fun decide(
            pkg: String,
            now: Instant,
        ): Decision? {
            val inputs = latestInputs
            return when {
                !running -> {
                    Decision.Allow
                }

                inputs == null -> {
                    null
                }

                else -> {
                    // The block screen polls every second: reuse the allowlist the last foreground evaluation built.
                    val list = cachedAllowlist ?: allowlist.allowlist()
                    RuleEvaluator.evaluate(context(pkg, now, inputs, list))
                }
            }
        }

        /** Storage and platform failures must not end enforcement: log, wait, start over. */
        private suspend fun runWithRetry() {
            while (true) {
                try {
                    run()
                    return
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    Log.e(TAG, "Blocking engine failed, retrying", e)
                    delay(RETRY_MS)
                }
            }
        }

        private suspend fun run() {
            val inputs =
                combine(
                    blocks.observeByTarget(BlockTarget.ThisPhone),
                    lockedApps.observeAll().map { all -> all.filter { it.target == BlockTarget.ThisPhone } },
                    breaks.observe(),
                    settings.settings.map { it.focusSession },
                    allowances.observe(),
                ) { b, l, br, f, a -> EngineInputs(b, l, br, f, a) }
            coroutineScope {
                launch { watchBreakEnd() }
                combine(tracker.foreground.map { it?.packageName }, inputs, clockChanges) { pkg, i, tick ->
                    Triple(pkg, i, tick)
                }.collectLatest { (pkg, i, tick) ->
                    latestInputs = i
                    if (tick != handledClockTick) {
                        handledClockTick = tick
                        discardClockJump()
                    }
                    evaluateWhileInFront(pkg, i)
                }
            }
        }

        /**
         * After a clock change the time since the last checkpoint is measured with the monotonic clock, which a
         * changed wall clock or zone does not move. It is booked at the timestamps it really happened at (the old
         * timeline, so a jump across midnight or an hour boundary cannot move it into another day), and counting
         * restarts from the new clock: the jump itself is never foreground time.
         */
        private suspend fun discardClockJump() {
            val s = session ?: return
            val inputs = bookingInputs
            val elapsed = (monotonic.elapsedMs() - s.flushedMono).coerceAtLeast(0L)
            if (s.counting && inputs != null && elapsed > 0L) {
                val from = s.flushedAt
                withContext(NonCancellable) { bookForeground(s.pkg, from, from.plusMillis(elapsed), inputs, inputs) }
            }
            s.flushedAt = clock.instant()
            s.flushedMono = monotonic.elapsedMs()
        }

        /**
         * Tells the user when a break runs out while the engine is watching. A break that is ended early or
         * replaced is not announced, and neither is one that had already elapsed before the engine first saw it.
         * A break the engine was already waiting for is still announced after a restart of the run, and the
         * wall clock is re-read after every wait so a clock that moved backwards never ends the break early.
         */
        private suspend fun watchBreakEnd() {
            // A clock change restarts the wait: the old delay still counts the previous clock.
            combine(breaks.observe(), clockChanges) { b, _ -> b }.collectLatest { session ->
                if (session == null) {
                    watchedBreakEnd = null
                    return@collectLatest
                }
                val end = session.endsAt
                if (session == announcedBreak) return@collectLatest // a clock moved back must not repeat the alert
                if (watchedBreakEnd != end) {
                    if (!end.isAfter(clock.instant())) return@collectLatest
                    watchedBreakEnd = end
                }
                while (true) {
                    val wait = Duration.between(clock.instant(), end).toMillis()
                    if (wait <= 0L) break
                    delay(wait)
                }
                watchedBreakEnd = null
                announcedBreak = session
                try {
                    breakEnd.breakEnded()
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    Log.e(TAG, "Could not announce the end of the break", e)
                }
            }
        }

        private suspend fun evaluateWhileInFront(
            pkg: String?,
            inputs: EngineInputs,
        ) {
            while (true) {
                val wait =
                    try {
                        evaluateOnce(pkg, inputs)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception,
                    ) {
                        Log.e(TAG, "Evaluation failed, retrying", e)
                        RETRY_MS
                    }
                delay(wait)
            }
        }

        /** One evaluation; returns how long to wait before the next one ([Long.MAX_VALUE]: until something changes). */
        private suspend fun evaluateOnce(
            pkg: String?,
            inputs: EngineInputs,
        ): Long {
            val now = clock.instant()
            val current = switchSession(pkg, now, inputs)
            if (pkg == null || current == null) {
                clearShown(now)
                return Long.MAX_VALUE
            }
            val list = allowlist.allowlist().also { cachedAllowlist = it }
            val ctx = context(pkg, now, inputs, list)
            when (val decision = RuleEvaluator.evaluate(ctx)) {
                Decision.Allow -> {
                    current.counting = true
                    clearShown(now)
                }

                is Decision.Block -> {
                    current.counting = false
                    enforce(current, decision, now)
                }
            }
            return nextWakeUp(ctx, inputs, now)
        }

        /**
         * Removes a shown block screen once the user has left the blocked app. Not within [DISMISS_GRACE_MS] of
         * showing it: the launcher reached by the block screen's own "home" jump must not cancel that screen.
         */
        private fun clearShown(now: Instant) {
            val s = shown ?: return
            shown = null
            if (Duration.between(s.at, now).toMillis() >= DISMISS_GRACE_MS) presenter.dismissOverlay()
        }

        private suspend fun enforce(
            current: Session,
            decision: Decision.Block,
            now: Instant,
        ) {
            val key = Shown(current, decision.reason, decision.blockId, now)
            val previous = shown
            if (previous != null && previous.copy(at = now) == key) return
            shown = key
            presenter.show(current.pkg, decision)
            // Not cancellable: an input change right now must not drop the record of a block that was shown.
            withContext(NonCancellable) {
                try {
                    events.record(current.pkg, decision.blockId, decision.reason.name, now)
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    Log.e(TAG, "Could not record block event", e)
                }
            }
        }

        /** Milliseconds until the decision may change, or [Long.MAX_VALUE] if only input changes can. */
        private fun nextWakeUp(
            ctx: EvaluationContext,
            inputs: EngineInputs,
            now: Instant,
        ): Long {
            val next = RuleEvaluator.nextEvaluationAt(ctx)?.let { Duration.between(now, it).toMillis() }
            val tracked =
                inputs.lockedApps.any { it.pkg == ctx.pkg } ||
                    inputs.blocks.any { it.enabled && ctx.pkg in it.apps }
            val capped = if (tracked) minOf(next ?: MAX_IDLE_MS, MAX_IDLE_MS) else next
            return capped?.coerceAtLeast(1L) ?: Long.MAX_VALUE
        }

        /**
         * Books the foreground time since the last evaluation and, if the app in front changed, starts a new
         * session. Returns the session of [pkg] (null when nothing is in front).
         *
         * The interval is booked against the inputs that were in force during it, not the freshly emitted ones, so
         * a cycle created just now does not retroactively charge time from before it existed.
         */
        private suspend fun switchSession(
            pkg: String?,
            now: Instant,
            inputs: EngineInputs,
        ): Session? {
            val previous = session
            val from = previous?.takeIf { it.counting }?.flushedAt
            val during = bookingInputs
            val next =
                when {
                    pkg == null -> null
                    previous?.pkg == pkg -> previous
                    else -> Session(pkg, now, monotonic.elapsedMs())
                }
            previous?.flushedAt = now
            previous?.flushedMono = monotonic.elapsedMs()
            session = next
            bookingInputs = inputs
            if (previous != null && from != null && during != null) {
                // The checkpoint above already moved: an input change in the middle of the booking must not lose it.
                if (now > from) withContext(NonCancellable) { bookForeground(previous.pkg, from, now, during, inputs) }
            }
            pruneCycleCache(inputs)
            unsaved.removeAll { key -> !cycleCache.containsKey(key) }
            return next
        }

        /** Drops cached cycle state of apps that are no longer part of a block (the DAO deletes it too). */
        private fun pruneCycleCache(inputs: EngineInputs) {
            cycleCache.keys.removeAll { key -> inputs.blocks.none { it.id == key.blockId && key.pkg in it.apps } }
        }

        private suspend fun bookForeground(
            pkg: String,
            from: Instant,
            to: Instant,
            during: EngineInputs,
            current: EngineInputs,
        ) {
            recorder.record(pkg, from, to)
            val delta = Duration.between(from, to).toMillis()
            during.blocks
                .filter { b ->
                    b.enabled && b.type == BlockType.CYCLE && pkg in b.apps &&
                        current.blocks.any { it.id == b.id && pkg in it.apps }
                }.forEach { block ->
                    try {
                        val key = CycleKey(block.id, pkg)
                        val before = cycleCache[key] ?: cycleStore.load(key) ?: CycleState()
                        val after = CycleStateMachine.advance(before, delta, to, block)
                        cycleCache[key] = after
                        if (after != before || key in unsaved) {
                            unsaved += key
                            cycleStore.save(key, after, to)
                            unsaved -= key
                        }
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception,
                    ) {
                        Log.e(TAG, "Could not persist cycle state of ${block.id}", e)
                    }
                }
        }

        private suspend fun context(
            pkg: String,
            now: Instant,
            inputs: EngineInputs,
            allowlist: Set<String>,
        ): EvaluationContext {
            val zoned = now.atZone(clock.zone)
            val tracked = inputs.blocks.filter { it.enabled && pkg in it.apps }
            val cycles = HashMap<CycleKey, CycleState>()
            for (block in tracked.filter { it.type == BlockType.CYCLE }) {
                val key = CycleKey(block.id, pkg)
                val state = cycleCache[key] ?: cycleStore.load(key)?.also { cycleCache[key] = it }
                if (state != null) cycles[key] = state
            }
            val pkgs = tracked.filter { it.type == BlockType.LIMIT }.flatMapTo(HashSet()) { it.apps }
            return EvaluationContext(
                now = zoned,
                pkg = pkg,
                blocks = inputs.blocks,
                lockedApps = inputs.lockedApps,
                breakSession = inputs.breakSession,
                focusSession = inputs.focusSession,
                allowances = inputs.allowances,
                usage = if (pkgs.isEmpty()) emptyMap() else usage.usage(pkgs, zoned),
                cycleStates = cycles,
                allowlist = allowlist,
            )
        }

        companion object {
            private const val TAG = "BlockingEngine"

            /** While a tracked app is in front the decision is re-checked at least this often. */
            const val MAX_IDLE_MS = 30_000L

            private const val RETRY_MS = 5_000L
            private const val DISMISS_GRACE_MS = 2_000L
        }
    }
