package com.dculus.stayfocused.core.blocking.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.dculus.stayfocused.core.blocking.evaluator.BlockReason
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-mdpi")
class BlockScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun shot(
        name: String,
        reason: BlockReason,
        timeLine: BlockTimeLine,
        cycleRule: CycleRuleMins? = null,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                BlockScreen(
                    pkg = "com.instagram.android",
                    appLabel = "Instagram",
                    reason = reason,
                    timeLine = timeLine,
                    onGoHome = {},
                    cycleRule = cycleRule,
                )
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun cycleRest() =
        shot("BlockCycleRest", BlockReason.CYCLE_REST, BlockTimeLine.Countdown("14:52"), CycleRuleMins(10, 30))

    @Test
    fun longCountdown() = shot("BlockLimitDaily", BlockReason.LIMIT_DAILY, BlockTimeLine.Countdown("05:12:09"))

    @Test
    fun schedule() = shot("BlockSchedule", BlockReason.SCHEDULE, BlockTimeLine.Until("18:30"))
}
