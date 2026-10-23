package com.dculus.stayfocused.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/** The whole v5 Home screen at the prototype's 390 dp width, tall enough that every card is composed. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h1300dp-xxhdpi")
class HomeDashboardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val taps = Taps()

    private fun show(
        state: HomeUiState,
        breakUi: BreakUi = BreakUi.Idle,
    ) {
        composeRule.setContent {
            Themed { HomeScreen(state, { breakUi }, taps.actions()) }
        }
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalInspectionMode provides true) { StayFocusedTheme { content() } }
    }

    private val typicalBlocks = blockRows(cycleBlock(), limitBlock(), scheduleBlock(enabled = true))
    private val child = device(alerts = 1, request = true)

    @Test fun typicalDay() {
        show(homeState(blocks = typicalBlocks, devices = listOf(child)))
        composeRule.onRoot().captureRoboImage("src/test/screenshots/HomeTypicalDay.png")
    }

    @Test fun breakRunning() {
        show(
            homeState(blocks = typicalBlocks, devices = listOf(child)),
            BreakUi.Active(remainingMs = 24 * 60_000L + 51_000L, totalMs = 30 * 60_000L),
        )
        composeRule.onRoot().captureRoboImage("src/test/screenshots/HomeBreakRunning.png")
    }

    @Test fun noUsageAccess() {
        show(homeState(gauge = GaugeUi.NoAccess, blocks = typicalBlocks))
        composeRule.onRoot().captureRoboImage("src/test/screenshots/HomeNoUsageAccess.png")
    }

    @Test fun noBlocksAndNoPhone() {
        show(homeState(gauge = readyGauge(avgMins = null), firstName = null))
        composeRule.onNodeWithTag(HOME_BLOCKS_EMPTY_TAG).assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/HomeNoBlocks.png")
    }

    @Test fun headerShowsTheDateAndGreetsByFirstName() {
        show(homeState())
        composeRule.onNodeWithText("Sunday, 18 October").assertExists()
        composeRule.onNodeWithText("Hi, Sam").assertExists()
    }

    @Test fun headerWithoutAnAccountHasNoName() {
        show(homeState(firstName = null))
        composeRule.onNodeWithText("Hi").assertExists()
    }

    @Test fun avatarOpensAccount() {
        show(homeState())
        composeRule.onNodeWithTag(HOME_AVATAR_TAG).performClick()
        assertEquals(listOf("account"), taps.log)
    }

    @Test fun bellIsHiddenWithoutALinkedPhone() {
        show(homeState())
        composeRule.onNodeWithTag(HOME_BELL_TAG).assertDoesNotExist()
    }

    @Test fun bellOpensThePhoneNeedingAttentionAndShowsItsBadge() {
        show(homeState(devices = listOf(device(id = "d1"), device(id = "d2", alerts = 1))))
        composeRule
            .onNodeWithTag(
                HOME_BELL_TAG,
            ).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Needs attention"))
        composeRule.onNodeWithTag(HOME_BELL_TAG).performClick()
        assertEquals(listOf("device:d2"), taps.log)
    }

    @Test fun bellOpensFamilyWhenNothingNeedsAttention() {
        show(homeState(devices = listOf(device())))
        composeRule
            .onNodeWithTag(
                HOME_BELL_TAG,
            ).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        composeRule.onNodeWithTag(HOME_BELL_TAG).performClick()
        assertEquals(listOf("devices"), taps.log)
    }

    @Test fun heroShowsTodayAgainstTheGoal() {
        show(homeState())
        composeRule.onNodeWithText("Screen time today").assertExists()
        composeRule.onNodeWithContentDescription("2h 33m").assertExists()
        composeRule.onNodeWithText("27 min under your 3h goal").assertExists()
        composeRule.onNodeWithText("Apps stopped today").assertExists()
        composeRule.onNodeWithText("Instagram").assertExists()
    }

    @Test fun overTheGoalSaysSo() {
        show(homeState(gauge = readyGauge(totalMins = 190)))
        composeRule.onNodeWithText("10 min over your 3h goal").assertExists()
    }

    @Test fun heroTilesOpenInsights() {
        show(homeState())
        composeRule.onNodeWithTag(HOME_BLOCKED_TILE_TAG).performClick()
        composeRule.onNodeWithTag(HOME_MOST_USED_TILE_TAG).performClick()
        assertEquals(listOf("insights", "insights"), taps.log)
    }

    @Test fun noUsageAccessOffersTheSettingsInsteadOfNumbers() {
        show(homeState(gauge = GaugeUi.NoAccess))
        composeRule.onNodeWithText("Usage access is off").assertExists()
        composeRule.onNodeWithTag(HOME_ALLOW_USAGE_TAG).performClick()
        assertEquals(listOf("allowUsage"), taps.log)
        composeRule.onNodeWithTag(HOME_BLOCKED_TILE_TAG).assertDoesNotExist()
    }

    @Test fun blockAppsShowsTheActiveBlockCountAndOpensTheWizard() {
        show(homeState(blocks = typicalBlocks))
        composeRule.onNodeWithText("3 on this phone").assertExists()
        composeRule.onNodeWithTag(HOME_NEW_BLOCK_TAG).performClick()
        assertEquals(listOf("newBlock"), taps.log)
    }

    @Test fun aiPillOpensDescribeAndIsHiddenWhileAiIsOff() {
        show(homeState())
        composeRule.onNodeWithTag(HOME_AI_DESCRIBE_TAG).performClick()
        assertEquals(listOf("ai"), taps.log)
    }

    @Test fun aiPillIsHiddenWhileAiIsOff() {
        show(homeState(aiAvailable = false))
        composeRule.onNodeWithTag(HOME_AI_DESCRIBE_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(HOME_NEW_BLOCK_TAG).assertExists()
    }

    @Test fun idleBreakTileOpensTheSheetAndStartingPassesTheChosenLength() {
        show(homeState())
        composeRule.onNodeWithTag(BREAK_IDLE_CARD_TAG).performClick()
        composeRule.onNodeWithText("Start 30 min break").assertExists()
        composeRule.onNodeWithText("1h").performClick()
        composeRule.onNodeWithTag(BREAK_START_TAG).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(60), taps.started)
    }

    @Test fun runningBreakShowsTheCountdownAndEndsOnTap() {
        show(homeState(), BreakUi.Active(remainingMs = 24 * 60_000L + 51_000L, totalMs = 30 * 60_000L))
        composeRule.onNodeWithText("On a\nbreak").assertExists()
        composeRule.onNodeWithTag(BREAK_COUNTDOWN_TAG, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("24:51").assertExists()
        composeRule.onNodeWithText("Tap to end early").assertExists()
        composeRule.onNodeWithTag(BREAK_ACTIVE_CARD_TAG).performClick()
        assertEquals(listOf("endBreak"), taps.log)
    }

    @Test fun childCardShowsItsNumbersAndOpensThePhone() {
        show(homeState(devices = listOf(child)))
        composeRule.onNodeWithTag(HOME_LINK_PHONE_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Aarav's phone").assertExists()
        composeRule.onNodeWithText("2:08").assertExists()
        composeRule.onNodeWithText("74%").assertExists()
        composeRule.onNodeWithText("Wants 30 more min on YouTube").assertExists()
        composeRule.onNodeWithText("Review").assertExists()
        composeRule.onNodeWithTag("$HOME_CHILD_CARD_TAG:d1").performClick()
        assertEquals(listOf("device:d1"), taps.log)
    }

    @Test fun anOfflinePhoneShowsNoActivity() {
        show(homeState(devices = listOf(device(online = false))))
        composeRule.onNodeWithText("Offline").assertExists()
        composeRule.onNodeWithText("YouTube").assertDoesNotExist()
    }

    @Test fun withoutAPhoneTheLinkRowLeadsToTheLinkFlow() {
        show(homeState())
        composeRule.onNodeWithTag(HOME_LINK_PHONE_TAG).performClick()
        assertEquals(listOf("link"), taps.log)
    }

    @Test fun extrasKeepTheAverageStatsAndBlockSwitches() {
        show(homeState(blocks = typicalBlocks))
        composeRule.onNodeWithTag(HOME_LIST_TAG).performScrollToNode(hasContentDescription("Work hours"))
        composeRule.onNodeWithText("▲ 14m over your 2h 19m avg").assertExists()
        composeRule.onNodeWithText("63").assertExists()
        composeRule.onNodeWithText("48").assertExists()
        composeRule.onNodeWithContentDescription("Mindful scrolling").performClick()
        composeRule.onNodeWithContentDescription("Work hours").performClick()
        assertEquals(listOf("b1" to false, "b3" to false), taps.toggled)
        composeRule.onNodeWithText("10m on, 30m off · Instagram, Youtube").assertExists()
        composeRule.onNodeWithTag(HOME_MANAGE_TAG).performClick()
        assertEquals(listOf("block"), taps.log)
    }

    @Test fun withoutHistoryThereIsNoAverageClaim() {
        show(homeState(gauge = readyGauge(avgMins = null)))
        composeRule.onNodeWithText("Average appears after a full day").assertExists()
    }
}
