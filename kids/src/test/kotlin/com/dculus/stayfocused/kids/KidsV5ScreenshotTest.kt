package com.dculus.stayfocused.kids

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.dculus.stayfocused.core.blocking.evaluator.BlockReason
import com.dculus.stayfocused.core.blocking.screen.BlockScreen
import com.dculus.stayfocused.core.blocking.screen.BlockTimeLine
import com.dculus.stayfocused.core.sync.KidsRule
import com.dculus.stayfocused.core.sync.KidsRuleType
import com.dculus.stayfocused.core.ui.v5.theme.V5Screen
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders of the v5 Kids screens, next to design/screens-v5/23-show-code.png and 24-allow-parent.png. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-mdpi")
class KidsV5ScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun shot(
        name: String,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent { CompositionLocalProvider(LocalInspectionMode provides true, content = content) }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun showCode() =
        shot("KidsShowCode") {
            KidsLinkScreen(KidsLinkState.Waiting("stayfocused://link?t=0123456789abcdef0123456789abcdef", "4:52"), {})
        }

    @Test
    fun allowParent() =
        shot("KidsAllowParent") {
            V5Screen(darkContent = false) {
                KidsConsentScreen(KidsConsentUiState(code = "482913", parentName = "Sam"), {}, {}, {})
            }
        }

    @Test
    fun home() =
        shot("KidsHome") {
            KidsHomeScreen(
                KidsHomeUiState(
                    parentName = "Sam",
                    todayMins = 72,
                    rules =
                        listOf(
                            KidsRule("1", "Instagram", KidsRuleType.Limit, limitMins = 30),
                            KidsRule(
                                "2",
                                "Games",
                                KidsRuleType.Schedule,
                                start = "08:00",
                                end = "15:00",
                                days = 0b0011111,
                            ),
                        ),
                ),
                {},
                {},
                {},
            )
        }

    @Test
    fun homeUnlinkSheet() =
        shot("KidsHomeUnlink") {
            KidsHomeScreen(KidsHomeUiState(parentName = "Sam", todayMins = 72, confirming = true), {}, {}, {})
        }

    @Test
    fun blockedAsk() = askShot("KidsBlockedAsk", AskStage.None)

    @Test
    fun blockedPick() = askShot("KidsBlockedPick", AskStage.Pick)

    @Test
    fun blockedSent() = askShot("KidsBlockedSent", AskStage.Sent)

    private fun askShot(
        name: String,
        stage: AskStage,
    ) = shot(name) {
        BlockScreen(
            pkg = "com.instagram.android",
            appLabel = "Instagram",
            reason = BlockReason.LIMIT_DAILY,
            timeLine = BlockTimeLine.None,
            onGoHome = {},
            extraActions = { AskForMoreTime("Sam", stage, {}, {}) },
        )
    }
}
