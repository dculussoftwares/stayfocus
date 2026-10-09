package com.dculus.stayfocused.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/** The whole Home screen at the prototype's 372 dp width, tall enough that every card is composed. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w372dp-h1120dp-xxhdpi")
class HomeDashboardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val taps = Taps()

    private fun show(
        state: HomeUiState,
        breakUi: BreakUi = BreakUi.Idle,
    ) {
        composeRule.setContent {
            Themed { HomeScreen(state, breakUi, taps.actions()) }
        }
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalInspectionMode provides true) { StayFocusedTheme { content() } }
    }

    private val typicalBlocks = blockRows(cycleBlock(), limitBlock(), scheduleBlock())

    @Test fun typicalDay() {
        show(homeState(blocks = typicalBlocks, devices = listOf(device(alerts = 1))))
        composeRule.onRoot().captureRoboImage("src/test/screenshots/HomeTypicalDay.png")
    }

    @Test fun noUsageAccess() {
        show(homeState(gauge = GaugeUi.NoAccess, blocks = typicalBlocks))
        composeRule.onRoot().captureRoboImage("src/test/screenshots/HomeNoUsageAccess.png")
    }

    @Test fun breakRunning() {
        show(
            homeState(blocks = typicalBlocks, devices = listOf(device())),
            BreakUi.Active(remainingMs = 18 * 60_000L + 4_000L, totalMs = 30 * 60_000L),
        )
        composeRule.onRoot().captureRoboImage("src/test/screenshots/HomeBreakRunning.png")
    }

    @Test fun noBlocks() {
        show(homeState(gauge = readyGauge(avgMins = null), firstName = null))
        composeRule.onNodeWithTag(HOME_BLOCKS_EMPTY_TAG).assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/HomeNoBlocks.png")
    }

    @Test fun headerGreetsByFirstName() {
        show(homeState())
        composeRule.onNodeWithText("TUE 06 OCT · 10:42").assertExists()
        composeRule.onNodeWithText("Morning, Sam").assertExists()
        composeRule.onNodeWithText("S").assertExists()
    }

    @Test fun headerWithoutAnAccountHasNoName() {
        show(homeState(firstName = null))
        composeRule.onNodeWithText("Morning").assertExists()
    }

    @Test fun avatarOpensAccount() {
        show(homeState())
        composeRule.onNodeWithTag(HOME_AVATAR_TAG).performClick()
        assertEquals(listOf("account"), taps.log)
    }

    @Test fun actionButtonsOpenTheWizardAndTheAiBox() {
        show(homeState())
        composeRule.onNodeWithTag(HOME_NEW_BLOCK_TAG).performClick()
        composeRule.onNodeWithTag(HOME_AI_DESCRIBE_TAG).performClick()
        assertEquals(listOf("newBlock", "ai"), taps.log)
    }

    @Test fun gaugeCardOpensInsightsAndShowsTodayAgainstTheAverage() {
        show(homeState())
        composeRule.onNodeWithText("2h 27m").assertExists()
        composeRule.onNodeWithText("▲ 8m over your 2h 19m avg").assertExists()
        composeRule.onNodeWithText("63").assertExists()
        composeRule.onNodeWithText("48").assertExists()
        composeRule.onNodeWithText("12").assertExists()
        composeRule.onNodeWithTag(HOME_GAUGE_CARD_TAG).performClick()
        assertEquals(listOf("insights"), taps.log)
    }

    @Test fun belowTheAverageSaysSo() {
        show(homeState(gauge = readyGauge(totalMins = 134, avgMins = 139)))
        composeRule.onNodeWithText("▼ 5m under your 2h 19m avg").assertExists()
    }

    @Test fun withoutHistoryThereIsNoAverageClaim() {
        show(homeState(gauge = readyGauge(avgMins = null)))
        composeRule.onNodeWithText("Average appears after a full day").assertExists()
    }

    @Test fun noUsageAccessOffersTheSettingsAndNoInsightsTap() {
        show(homeState(gauge = GaugeUi.NoAccess))
        composeRule.onNodeWithText("Usage access is off").assertExists()
        composeRule.onNodeWithTag(HOME_ALLOW_USAGE_TAG).performClick()
        assertEquals(listOf("allowUsage"), taps.log)
        composeRule.onNodeWithText("INSIGHTS →").assertDoesNotExist()
    }

    @Test fun blockSwitchesAreWiredToTheRepository() {
        show(homeState(blocks = typicalBlocks))
        composeRule.onNodeWithContentDescription("Mindful scrolling").performClick()
        composeRule.onNodeWithContentDescription("Work hours").performClick()
        assertEquals(listOf("b1" to false, "b3" to true), taps.toggled)
        composeRule.onNodeWithText("10m on, 30m off · Instagram, Youtube").assertExists()
    }

    @Test fun manageOpensTheBlockTab() {
        show(homeState(blocks = typicalBlocks))
        composeRule.onNodeWithTag(HOME_MANAGE_TAG).performClick()
        assertEquals(listOf("block"), taps.log)
    }

    @Test fun emptyLinkedPhonesLeadToDevices() {
        show(homeState())
        composeRule.onNodeWithTag(HOME_LINK_PHONE_TAG).performClick()
        composeRule.onNodeWithTag(HOME_ALL_DEVICES_TAG).performClick()
        assertEquals(listOf("devices", "devices"), taps.log)
    }

    @Test fun linkedPhoneRowOpensItAndShowsItsAlertBadge() {
        show(homeState(devices = listOf(device(alerts = 2))))
        composeRule.onNodeWithTag(HOME_LINK_PHONE_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("80% · INSTAGRAM").assertExists()
        composeRule.onNodeWithText("2").assertExists()
        composeRule.onNodeWithText("Aarav's phone").performClick()
        assertEquals(listOf("device:d1"), taps.log)
    }
}
