package com.dculus.stayfocused.core.blocking.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dculus.stayfocused.core.blocking.evaluator.Decision
import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Inject

/**
 * Re-evaluates a block while its screen is visible (once per second). Returns [Decision.Allow] when the
 * block is over, which makes the screen finish itself. The engine story replaces the default binding with
 * one that runs the real `RuleEvaluator`.
 */
fun interface BlockDecisionSource {
    suspend fun current(
        pkg: String,
        initial: Decision.Block,
        now: Instant,
    ): Decision
}

/** Default: the block holds until its end time; nothing else is known here. */
class TimeBasedBlockDecisionSource
    @Inject
    constructor() : BlockDecisionSource {
        override suspend fun current(
            pkg: String,
            initial: Decision.Block,
            now: Instant,
        ): Decision = if (initial.until != null && !now.isBefore(initial.until)) Decision.Allow else initial
    }

/** Slot under the "Go to home screen" button. Kids binds one (M8-05); `:app` binds none, so nothing shows. */
interface BlockScreenExtras {
    @Composable
    fun Content(
        pkg: String,
        decision: Decision.Block,
        modifier: Modifier,
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BlockScreenModule {
    @BindsOptionalOf
    abstract fun extras(): BlockScreenExtras
}
