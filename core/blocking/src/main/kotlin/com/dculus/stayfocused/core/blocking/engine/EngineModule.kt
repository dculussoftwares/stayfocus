package com.dculus.stayfocused.core.blocking.engine

import android.os.SystemClock
import com.dculus.stayfocused.core.blocking.evaluator.Decision
import com.dculus.stayfocused.core.blocking.screen.BlockDecisionSource
import com.dculus.stayfocused.core.blocking.screen.BlockPresenter
import com.dculus.stayfocused.core.blocking.screen.BlockScreenLauncher
import com.dculus.stayfocused.core.blocking.screen.TimeBasedBlockDecisionSource
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.time.Instant
import javax.inject.Inject
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EngineDispatcher

/**
 * Block screen decisions come from the engine's rule evaluation, so the screen finishes exactly when the rule
 * says so. Falls back to the end time while the engine has no inputs. [Lazy]: the engine depends on the launcher,
 * which depends on this source.
 */
class EngineBlockDecisionSource
    @Inject
    internal constructor(
        private val engine: Lazy<BlockingEngine>,
        private val fallback: TimeBasedBlockDecisionSource,
    ) : BlockDecisionSource {
        override suspend fun current(
            pkg: String,
            initial: Decision.Block,
            now: Instant,
        ): Decision = engine.get().decide(pkg, now) ?: fallback.current(pkg, initial, now)
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class EngineModule {
    @Binds
    abstract fun decisionSource(impl: EngineBlockDecisionSource): BlockDecisionSource

    @Binds
    abstract fun presenter(impl: BlockScreenLauncher): BlockPresenter

    @Binds
    abstract fun usageProvider(impl: UsageStatsAppUsageProvider): AppUsageProvider

    @Binds
    abstract fun liveForeground(impl: ForegroundTimingUsage): LiveForegroundSource

    @Binds
    abstract fun recorder(impl: ForegroundTimingUsage): ForegroundTimeRecorder

    @Binds
    abstract fun allowlist(impl: AndroidBlockAllowlistProvider): BlockAllowlistProvider

    @Binds
    abstract fun cycleStore(impl: RoomCycleStateStore): CycleStateStore

    @Binds
    abstract fun breakEnd(impl: AndroidBreakNotifier): BreakEndNotifier

    /** Approved "more time" grants (M8-05); the Kids app gives them, the engine reads them. */
    @Binds
    abstract fun allowances(impl: SharedPreferencesTemporaryAllowances): TemporaryAllowances

    @Binds
    abstract fun allowanceSource(impl: TemporaryAllowances): TemporaryAllowanceSource

    @Binds
    abstract fun eventLog(impl: RoomBlockEventLog): BlockEventLog

    companion object {
        @Provides
        @EngineDispatcher
        fun dispatcher(): CoroutineDispatcher = Dispatchers.Default

        @Provides
        fun monotonic(): MonotonicClock = MonotonicClock { SystemClock.elapsedRealtime() }
    }
}
