package com.dculus.stayfocused.core.blocking.engine

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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
 * Lives exactly as long as the accessibility service: [start] on connect, [stop] on unbind/destroy.
 */
@Singleton
@Suppress("LongParameterList") // one collaborator per input; all of them are interfaces
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
        private val clock: Clock,
        @EngineDispatcher private val dispatcher: CoroutineDispatcher,
    ) {
        /** An uninterrupted stay of [pkg] in front. [counting] is false while it is being blocked. */
        private class Session(
            val pkg: String,
            var flushedAt: Instant,
            var counting: Boolean = true,
        )

        private data class Shown(
            val session: Session,
            val reason: BlockReason,
            val blockId: String?,
        )

        private val lock = Any()
        private var scope: CoroutineScope? = null

        // Only touched from the engine coroutine, which collectLatest runs one iteration at a time.
        private var session: Session? = null
        private var sessionInputs: EngineInputs? = null
        private var shown: Shown? = null
        private val cycleCache = ConcurrentHashMap<CycleKey, CycleState>()

        @Volatile private var latestInputs: EngineInputs? = null

        fun start() {
            synchronized(lock) {
                if (scope != null) return
                val s = CoroutineScope(SupervisorJob() + dispatcher)
                scope = s
                s.launch { run() }
            }
        }

        /** Cancels everything the engine started. Safe to call repeatedly. */
        fun stop() {
            val s =
                synchronized(lock) {
                    scope.also { scope = null }
                } ?: return
            runBlocking {
                withContext(NonCancellable) {
                    val current = session
                    val from = current?.takeIf { it.counting }?.flushedAt
                    val inputs = sessionInputs ?: latestInputs
                    if (current != null && from != null && inputs != null) {
                        val now = clock.instant()
                        if (now > from) {
                            bookForeground(current.pkg, from, now, inputs)
                            current.flushedAt = now
                        }
                    }
                    session = null
                    sessionInputs = null
                }
            }
            s.cancel()
            shown = null
            latestInputs = null
            cycleCache.clear()
            presenter.dismissOverlay()
        }

        /**
         * Re-evaluates [pkg] at [now] with the most recent inputs; used by the block screen so it ends exactly when
         * the rule says so. Null while the engine has not seen its inputs yet.
         */
        internal suspend fun decide(
            pkg: String,
            now: Instant,
        ): Decision? {
            val inputs = latestInputs ?: return null
            return RuleEvaluator.evaluate(context(pkg, now, inputs))
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
            combine(tracker.foreground.map { it?.packageName }, inputs) { pkg, i -> pkg to i }
                .collectLatest { (pkg, i) ->
                    latestInputs = i
                    val activeCycleKeys =
                        i.blocks
                            .filter { it.type == BlockType.CYCLE }
                            .flatMapTo(HashSet()) { block -> block.apps.map { CycleKey(block.id, it) } }
                    cycleCache.keys
                        .filter { it !in activeCycleKeys }
                        .forEach { cycleCache.remove(it) }
                    evaluateWhileInFront(pkg, i)
                }
        }

        private suspend fun evaluateWhileInFront(
            pkg: String?,
            inputs: EngineInputs,
        ) {
            while (true) {
                val now = clock.instant()
                val current = switchSession(pkg, now, inputs)
                if (pkg == null || current == null) {
                    clearShown()
                    return
                }
                val ctx = context(pkg, now, inputs)
                when (val decision = RuleEvaluator.evaluate(ctx)) {
                    Decision.Allow -> {
                        current.counting = true
                        clearShown()
                    }

                    is Decision.Block -> {
                        current.counting = false
                        enforce(current, decision, now)
                    }
                }
                val wait = nextWakeUp(ctx, inputs, now) ?: awaitCancellation()
                delay(wait)
            }
        }

        private fun clearShown() {
            if (shown != null) {
                shown = null
                presenter.dismissOverlay()
            }
        }

        private suspend fun enforce(
            current: Session,
            decision: Decision.Block,
            now: Instant,
        ) {
            val key = Shown(current, decision.reason, decision.blockId)
            if (shown == key) return
            shown = key
            presenter.show(current.pkg, decision)
            events.record(current.pkg, decision.blockId, decision.reason.name, now)
        }

        /** Milliseconds until the decision for the foreground app may change, or null if only input changes can. */
        private fun nextWakeUp(
            ctx: EvaluationContext,
            inputs: EngineInputs,
            now: Instant,
        ): Long? {
            val next = RuleEvaluator.nextEvaluationAt(ctx)?.let { Duration.between(now, it).toMillis() }
            val tracked =
                inputs.lockedApps.any { it.pkg == ctx.pkg } ||
                    inputs.blocks.any { it.enabled && ctx.pkg in it.apps }
            val capped = if (tracked) minOf(next ?: MAX_IDLE_MS, MAX_IDLE_MS) else next
            return capped?.coerceAtLeast(1L)
        }

        /**
         * Books the foreground time since the last evaluation and, if the app in front changed, starts a new
         * session. Returns the session of [pkg] (null when nothing is in front).
         */
        private suspend fun switchSession(
            pkg: String?,
            now: Instant,
            inputs: EngineInputs,
        ): Session? {
            val previous = session
            val from = previous?.takeIf { it.counting }?.flushedAt
            val previousInputs = sessionInputs
            val next =
                when {
                    pkg == null -> null
                    previous?.pkg == pkg -> previous
                    else -> Session(pkg, now)
                }
            // Keep the whole flush and checkpoint update non-cancellable. Otherwise a new input emission can
            // cancel the booking after the timestamp has moved, losing the interval on the next evaluation.
            withContext(NonCancellable) {
                if (previous != null && from != null && now > from) {
                    bookForeground(previous.pkg, from, now, previousInputs ?: inputs)
                    previous.flushedAt = now
                }
                session = next
                sessionInputs = if (next == null) null else inputs
            }
            return next
        }

        private suspend fun bookForeground(
            pkg: String,
            from: Instant,
            to: Instant,
            inputs: EngineInputs,
        ) {
            recorder.record(pkg, from, to)
            val delta = Duration.between(from, to).toMillis()
            inputs.blocks
                .filter { it.enabled && it.type == BlockType.CYCLE && pkg in it.apps }
                .forEach { block ->
                    val key = CycleKey(block.id, pkg)
                    val before = cycleCache[key] ?: cycleStore.load(key) ?: CycleState()
                    val after = CycleStateMachine.advance(before, delta, to, block)
                    cycleCache[key] = after
                    if (after != before) {
                        withContext(NonCancellable) { cycleStore.save(key, after, to) }
                    }
                }
        }

        private suspend fun context(
            pkg: String,
            now: Instant,
            inputs: EngineInputs,
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
                allowlist = allowlist.allowlist(),
            )
        }

        companion object {
            /** While a tracked app is in front the decision is re-checked at least this often. */
            const val MAX_IDLE_MS = 30_000L
        }
    }
