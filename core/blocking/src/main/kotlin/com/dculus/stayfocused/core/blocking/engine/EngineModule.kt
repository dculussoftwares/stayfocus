package com.dculus.stayfocused.core.blocking.engine

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
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

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
    abstract fun usageProvider(impl: ForegroundTimingUsage): AppUsageProvider

    @Binds
    abstract fun recorder(impl: ForegroundTimingUsage): ForegroundTimeRecorder

    @Binds
    abstract fun allowlist(impl: AndroidBlockAllowlistProvider): BlockAllowlistProvider

    @Binds
    abstract fun cycleStore(impl: RoomCycleStateStore): CycleStateStore

    @Binds
    abstract fun eventLog(impl: RoomBlockEventLog): BlockEventLog

    companion object {
        @Provides
        @EngineDispatcher
        fun dispatcher(): CoroutineDispatcher = Dispatchers.Default

        /** Nothing grants temporary allowances yet (unlock requests land in a later story). */
        @Provides
        @Singleton
        fun allowances(): TemporaryAllowanceSource = TemporaryAllowanceSource { flowOf(emptyList()) }
    }
}
