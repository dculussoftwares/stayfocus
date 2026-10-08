package com.dculus.stayfocused.core.blocking.engine

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.telecom.TelecomManager
import com.dculus.stayfocused.core.blocking.ForegroundAppTracker
import com.dculus.stayfocused.core.blocking.ForegroundEnvironment
import com.dculus.stayfocused.core.blocking.evaluator.BlockReason
import com.dculus.stayfocused.core.blocking.evaluator.CycleKey
import com.dculus.stayfocused.core.blocking.evaluator.CycleState
import com.dculus.stayfocused.core.blocking.evaluator.Decision
import com.dculus.stayfocused.core.blocking.screen.BlockPresenter
import com.dculus.stayfocused.core.model.Block
import com.dculus.stayfocused.core.model.BlockSource
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.DaysOfWeek
import com.dculus.stayfocused.core.model.LimitPeriod
import com.dculus.stayfocused.core.model.TimeRange
import com.dculus.stayfocused.core.testing.FakeBlockRepository
import com.dculus.stayfocused.core.testing.FakeBreakRepository
import com.dculus.stayfocused.core.testing.FakeLockedAppsRepository
import com.dculus.stayfocused.core.testing.FakeSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val APP = "com.social"
private const val OTHER = "com.news"
private const val LAUNCHER = "com.launcher"
private const val DIALER = "com.dialer"

/** Tuesday 2023-11-14 09:59:00Z. */
private val START: Instant = Instant.parse("2023-11-14T09:59:00Z")

private class FakeEnv : ForegroundEnvironment {
    override val ownPackage = "com.dculus.stayfocused"

    override fun imePackages(): Set<String> = emptySet()

    override fun isActivity(
        packageName: String,
        className: String,
    ): Boolean = true

    override fun nowMillis(): Long = 0L
}

private class RecordingPresenter : BlockPresenter {
    val shown = mutableListOf<Pair<String, Decision.Block>>()
    var dismissals = 0

    override fun show(
        pkg: String,
        decision: Decision.Block,
    ) {
        shown += pkg to decision
    }

    override fun dismissOverlay() {
        dismissals++
    }
}

private class MemoryCycleStore : CycleStateStore {
    val states = HashMap<CycleKey, CycleState>()
    var failSaves = false

    override suspend fun load(key: CycleKey): CycleState? = states[key]

    override suspend fun save(
        key: CycleKey,
        state: CycleState,
        now: Instant,
    ) {
        check(!failSaves) { "cycle store unavailable" }
        states[key] = state
    }
}

/** Wall clock that follows the test scheduler's virtual time, so timers and `Instant.now` agree. */
@OptIn(ExperimentalCoroutinesApi::class)
private class VirtualClock(
    private val scope: TestScope,
) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = START.plusMillis(scope.testScheduler.currentTime)
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BlockingEngineTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    private class Rig(
        val scope: TestScope,
        context: Context,
    ) {
        val clock = VirtualClock(scope)
        val tracker = ForegroundAppTracker(FakeEnv())
        val blocks = FakeBlockRepository()
        val locked = FakeLockedAppsRepository(clock)
        val breaks = FakeBreakRepository(clock)
        val settings = FakeSettingsRepository()
        val presenter = RecordingPresenter()
        val cycles = MemoryCycleStore()
        val events = mutableListOf<Triple<String, String?, String>>()
        var failEvents = false
        var breakEnds = 0
        val usage = ForegroundTimingUsage()
        val engine =
            BlockingEngine(
                tracker,
                blocks,
                locked,
                breaks,
                settings,
                { flowOf(emptyList()) },
                cycles,
                { pkg, blockId, reason, _ ->
                    check(!failEvents) { "event log unavailable" }
                    events += Triple(pkg, blockId, reason)
                },
                usage,
                usage,
                AndroidBlockAllowlistProvider(context),
                presenter,
                { breakEnds++ },
                clock,
                StandardTestDispatcher(scope.testScheduler),
            )

        fun open(pkg: String) = tracker.onWindowStateChanged(pkg, "$pkg.Main")

        suspend fun pass(ms: Long) {
            scope.advanceTimeBy(ms)
            scope.runCurrent()
        }
    }

    private fun block(
        id: String,
        type: BlockType,
        apps: Set<String> = setOf(APP),
        limitMins: Int? = null,
        period: LimitPeriod? = null,
        useMins: Int? = null,
        restMins: Int? = null,
        range: TimeRange? = null,
        durationMins: Int? = null,
        startedAt: Instant? = null,
    ) = Block(
        id = id,
        target = BlockTarget.ThisPhone,
        type = type,
        name = id,
        apps = apps,
        limitMins = limitMins,
        period = period,
        useMins = useMins,
        restMins = restMins,
        range = range,
        durationMins = durationMins,
        startedAt = startedAt,
        days = DaysOfWeek.ALL,
        enabled = true,
        createdAt = START,
        source = BlockSource.MANUAL,
    )

    private fun rig(
        scope: TestScope,
        launcherPackage: String = LAUNCHER,
    ): Rig {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = launcherPackage } }
        shadowOf(context.packageManager).addResolveInfoForIntent(home, info)
        return Rig(scope, context)
    }

    @Test fun nowBlockStartsAndEndsWhileAppStaysOpen() =
        runTest {
            val r = rig(this)
            r.engine.start()
            r.open(APP)
            r.pass(1_000)
            assertTrue(r.presenter.shown.isEmpty(), "no block yet")

            r.blocks.upsert(
                block("now", BlockType.NOW, durationMins = 10, startedAt = r.clock.instant()),
            )
            r.pass(1_000)
            assertEquals(1, r.presenter.shown.size)
            val (pkg, decision) = r.presenter.shown.single()
            assertEquals(APP, pkg)
            assertEquals(BlockReason.NOW, decision.reason)
            assertEquals(listOf(Triple<String, String?, String>(APP, "now", "NOW")), r.events)

            // The block screen's own "home" jump lands on the launcher right away: that must not dismiss it.
            r.open(LAUNCHER)
            r.pass(1_000)
            assertEquals(0, r.presenter.dismissals)

            // Opening the app again inside the block shows it again; after the block it is let through.
            r.open(APP)
            r.pass(1_000)
            assertEquals(2, r.presenter.shown.size)
            r.open(LAUNCHER)
            r.pass(10 * 60_000L)
            r.open(APP)
            r.pass(60_000)
            assertEquals(2, r.presenter.shown.size)
            assertEquals(2, r.events.size)
            r.engine.stop()
        }

    @Test fun scheduleBoundaryWhileAppStaysOpenTriggersBlock() =
        runTest {
            val r = rig(this)
            r.blocks.upsert(
                block("sched", BlockType.SCHEDULE, range = TimeRange(LocalTime.of(10, 0), LocalTime.of(11, 0))),
            )
            r.engine.start()
            r.open(APP)
            r.pass(30_000)
            assertTrue(r.presenter.shown.isEmpty())

            // 09:59:30 -> the timer was re-armed for 10:00:00 and fires without any foreground event.
            r.pass(30_000)
            assertEquals(1, r.presenter.shown.size)
            assertEquals(
                BlockReason.SCHEDULE,
                r.presenter.shown
                    .single()
                    .second.reason,
            )
            r.engine.stop()
        }

    @Test fun timerIsRearmedAfterEachEvaluation() =
        runTest {
            val r = rig(this)
            // Two separate windows: a block that ends at 10:00 and one that starts at 10:30.
            r.blocks.upsert(
                block("a", BlockType.SCHEDULE, range = TimeRange(LocalTime.of(9, 0), LocalTime.of(10, 0))),
            )
            r.blocks.upsert(
                block("b", BlockType.SCHEDULE, range = TimeRange(LocalTime.of(10, 30), LocalTime.of(11, 0))),
            )
            r.engine.start()
            r.open(APP)
            r.pass(1_000)
            assertEquals(1, r.presenter.shown.size) // 09:59 is inside "a"
            r.open(LAUNCHER)
            r.pass(1_000)
            r.pass(2 * 60_000L)
            r.open(APP) // about 10:01: allowed again; the timer must now wait for 10:30
            r.pass(1_000)
            assertEquals(1, r.presenter.shown.size)
            r.pass(28 * 60_000L)
            assertEquals(1, r.presenter.shown.size)
            r.pass(2 * 60_000L)
            assertEquals(2, r.presenter.shown.size)
            assertEquals(
                "b",
                r.presenter.shown
                    .last()
                    .second.blockId,
            )
            r.engine.stop()
        }

    @Test fun cycleBlockCountsForegroundTimeAndPersistsIt() =
        runTest {
            val r = rig(this)
            r.blocks.upsert(block("cycle", BlockType.CYCLE, useMins = 1, restMins = 5))
            r.engine.start()
            r.open(APP)
            r.pass(30_000)
            assertTrue(r.presenter.shown.isEmpty())
            assertEquals(30_000L, r.cycles.states[CycleKey("cycle", APP)]?.usedMs)

            r.pass(30_000)
            assertEquals(1, r.presenter.shown.size)
            assertEquals(
                BlockReason.CYCLE_REST,
                r.presenter.shown
                    .single()
                    .second.reason,
            )
            val state = r.cycles.states.getValue(CycleKey("cycle", APP))
            assertEquals(r.clock.instant().plusSeconds(5 * 60), state.lockedUntil)
            r.engine.stop()
        }

    @Test fun dailyLimitUsesEngineMeasuredForegroundTime() =
        runTest {
            val r = rig(this)
            r.blocks.upsert(block("limit", BlockType.LIMIT, limitMins = 1, period = LimitPeriod.DAILY))
            r.engine.start()
            r.open(APP)
            r.pass(45_000)
            assertTrue(r.presenter.shown.isEmpty())
            r.pass(15_000)
            assertEquals(
                BlockReason.LIMIT_DAILY,
                r.presenter.shown
                    .single()
                    .second.reason,
            )
            r.engine.stop()
        }

    @Test fun breakBlocksEverythingButTheAllowlistAndEndsOnTime() =
        runTest {
            val r = rig(this)
            shadowOf(context.getSystemService(TelecomManager::class.java)).setDefaultDialer(DIALER)
            r.engine.start()
            r.open(APP)
            r.pass(1_000)
            assertTrue(r.presenter.shown.isEmpty(), "no break yet")

            r.breaks.start(5)
            r.pass(1_000)
            val (pkg, decision) = r.presenter.shown.single()
            assertEquals(APP, pkg)
            assertEquals(BlockReason.BREAK, decision.reason)

            r.open(DIALER)
            r.pass(1_000)
            assertEquals(1, r.presenter.shown.size, "the dialer stays reachable during a break")

            r.open(LAUNCHER)
            r.pass(5 * 60_000L)
            r.open(APP)
            r.pass(1_000)
            assertEquals(1, r.presenter.shown.size, "opens normally after the break")
            r.engine.stop()
        }

    @Test fun breakEndIsAnnouncedOnceWhenItRunsOut() =
        runTest {
            val r = rig(this)
            r.engine.start()
            r.breaks.start(5)
            r.pass(5 * 60_000L - 1)
            assertEquals(0, r.breakEnds)
            r.pass(2)
            assertEquals(1, r.breakEnds)
            r.pass(10 * 60_000L)
            assertEquals(1, r.breakEnds)
            r.engine.stop()
        }

    @Test fun breakEndIsStillAnnouncedAfterTheEngineIsRestartedMidBreak() =
        runTest {
            val r = rig(this)
            r.engine.start()
            r.breaks.start(5)
            r.pass(60_000)
            r.engine.stop()
            r.pass(1_000)
            r.engine.start()
            r.pass(5 * 60_000L)
            assertEquals(1, r.breakEnds)
            r.engine.stop()
        }

    @Test fun breakEndedEarlyOrAlreadyElapsedIsNotAnnounced() =
        runTest {
            val r = rig(this)
            r.engine.start()
            r.breaks.start(5)
            r.pass(60_000)
            r.breaks.end()
            r.pass(10 * 60_000L)
            assertEquals(0, r.breakEnds)
            r.engine.stop()

            // An elapsed break that is still stored when the engine starts is old news.
            val again = Rig(this, context).also { it.breaks.start(1) }
            again.pass(2 * 60_000L)
            again.engine.start()
            again.pass(60_000)
            assertEquals(0, again.breakEnds)
            again.engine.stop()
        }

    @Test fun allowlistedAppsAreNeverBlocked() =
        runTest {
            val r = rig(this)
            r.locked.lock(LAUNCHER, BlockTarget.ThisPhone)
            r.locked.lock("com.android.settings", BlockTarget.ThisPhone)
            r.engine.start()
            r.open(LAUNCHER)
            r.pass(1_000)
            r.open("com.android.settings")
            r.pass(1_000)
            assertTrue(r.presenter.shown.isEmpty())
            r.locked.lock(OTHER, BlockTarget.ThisPhone)
            r.open(OTHER)
            r.pass(1_000)
            assertEquals(
                BlockReason.MANUAL_LOCK,
                r.presenter.shown
                    .single()
                    .second.reason,
            )
            r.engine.stop()
        }

    @Test fun stopCancelsEverythingAndLeavesNoPendingWork() =
        runTest {
            val r = rig(this)
            r.blocks.upsert(
                block("sched", BlockType.SCHEDULE, range = TimeRange(LocalTime.of(10, 0), LocalTime.of(11, 0))),
            )
            r.engine.start()
            r.open(APP)
            r.pass(1_000)
            r.engine.stop()

            r.pass(5 * 60_000L)
            assertTrue(r.presenter.shown.isEmpty(), "a stopped engine must not evaluate")
            // Nothing is left in the scheduler: a leaked timer or collector would still be queued.
            // A leaked timer loop would keep this from ever returning (runTest then times out).
            testScheduler.advanceUntilIdle()
        }

    @Test fun startIsIdempotentAndEngineCanRestart() =
        runTest {
            val r = rig(this)
            r.blocks.upsert(block("now", BlockType.NOW, durationMins = 10, startedAt = START))
            r.engine.start()
            r.engine.start()
            r.open(APP)
            r.pass(1_000)
            assertEquals(1, r.presenter.shown.size)
            r.engine.stop()
            r.engine.start()
            r.pass(1_000)
            assertEquals(2, r.presenter.shown.size)
            r.engine.stop()
        }

    @Test fun leavingTheBlockedAppLaterDismissesTheOverlay() =
        runTest {
            val r = rig(this)
            r.locked.lock(APP, BlockTarget.ThisPhone)
            r.engine.start()
            r.open(APP)
            r.pass(5_000)
            assertEquals(0, r.presenter.dismissals)
            r.open(LAUNCHER)
            r.pass(1_000)
            assertEquals(1, r.presenter.dismissals)
            r.engine.stop()
        }

    @Test fun cycleCreatedMidSessionDoesNotChargeEarlierTime() =
        runTest {
            val r = rig(this)
            r.engine.start()
            r.open(APP)
            r.pass(5 * 60_000L)
            r.blocks.upsert(block("cycle", BlockType.CYCLE, useMins = 1, restMins = 5))
            r.pass(1_000)
            assertTrue(r.presenter.shown.isEmpty(), "five minutes before the rule existed must not count")
            assertTrue((r.cycles.states[CycleKey("cycle", APP)]?.usedMs ?: 0L) <= 1_000L)
            r.engine.stop()
        }

    @Test fun stopBooksTheTimeSinceTheLastEvaluation() =
        runTest {
            val r = rig(this)
            r.blocks.upsert(block("cycle", BlockType.CYCLE, useMins = 10, restMins = 5))
            r.engine.start()
            r.open(APP)
            r.pass(10_000)
            r.engine.stop()
            r.pass(1_000)
            assertEquals(10_000L, r.cycles.states[CycleKey("cycle", APP)]?.usedMs)
            val today =
                r.usage
                    .usage(setOf(APP), r.clock.instant().atZone(ZoneOffset.UTC))
                    .getValue(APP)
                    .todayMs
            assertEquals(10_000L, today)
        }

    @Test fun blockScreenDecisionAllowsOnceStopped() =
        runTest {
            val r = rig(this)
            r.locked.lock(APP, BlockTarget.ThisPhone)
            r.engine.start()
            r.open(APP)
            r.pass(1_000)
            assertTrue(r.engine.decide(APP, r.clock.instant()) is Decision.Block)
            r.engine.stop()
            assertEquals(Decision.Allow, r.engine.decide(APP, r.clock.instant()))
        }

    @Test fun storageFailureDoesNotEndEnforcement() =
        runTest {
            val r = rig(this)
            r.cycles.failSaves = true
            r.failEvents = true
            r.blocks.upsert(block("cycle", BlockType.CYCLE, useMins = 1, restMins = 5))
            r.engine.start()
            r.open(APP)
            r.pass(60_000)
            assertEquals(
                BlockReason.CYCLE_REST,
                r.presenter.shown
                    .single()
                    .second.reason,
            )
            r.engine.stop()
        }

    @Test fun reAddedAppStartsWithFreshCycleState() =
        runTest {
            val r = rig(this)
            r.blocks.upsert(block("cycle", BlockType.CYCLE, useMins = 1, restMins = 5))
            r.engine.start()
            r.open(APP)
            r.pass(60_000)
            assertEquals(1, r.presenter.shown.size)
            r.open(LAUNCHER)
            r.pass(1_000)
            // Removing the app deletes its stored state (Room does that in the DAO); re-adding must not resurrect it.
            r.blocks.upsert(block("cycle", BlockType.CYCLE, apps = setOf(OTHER), useMins = 1, restMins = 5))
            r.pass(1_000)
            r.cycles.states.clear()
            r.blocks.upsert(block("cycle", BlockType.CYCLE, useMins = 1, restMins = 5))
            r.pass(1_000)
            r.open(APP)
            r.pass(1_000)
            assertEquals(1, r.presenter.shown.size, "rest of the old window must be gone")
            r.engine.stop()
        }

    @Test fun failedCycleSaveIsRetriedWhenStopping() =
        runTest {
            val r = rig(this)
            r.blocks.upsert(block("cycle", BlockType.CYCLE, useMins = 10, restMins = 5))
            r.cycles.failSaves = true
            r.engine.start()
            r.open(APP)
            r.pass(30_000)
            assertTrue(r.cycles.states.isEmpty())
            r.cycles.failSaves = false
            r.pass(5_000)
            r.engine.stop()
            r.pass(1_000)
            assertEquals(35_000L, r.cycles.states[CycleKey("cycle", APP)]?.usedMs)
        }

    @Test fun stopBooksUpToTheStopInstant() =
        runTest {
            val r = rig(this)
            r.blocks.upsert(block("cycle", BlockType.CYCLE, useMins = 10, restMins = 5))
            r.engine.start()
            r.open(APP)
            r.pass(10_000)
            r.engine.stop()
            // The shutdown job only runs once time moves on; the extra minute must not be charged.
            r.pass(60_000)
            assertEquals(10_000L, r.cycles.states[CycleKey("cycle", APP)]?.usedMs)
        }
}
