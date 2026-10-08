package com.dculus.stayfocused.feature.insights

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class InsightsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun shot(
        name: String,
        state: InsightsUiState,
        content: @Composable (InsightsUiState) -> Unit = { InsightsContent(it, {}, {}, {}) },
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) { StayFocusedTheme { content(state) } }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun screenTime() = shot("InsightsScreenTime", previewInsightsState(InsightsMetric.ScreenTime))

    @Test fun opens() = shot("InsightsOpens", previewInsightsState(InsightsMetric.Opens))

    @Test fun unlocks() = shot("InsightsUnlocks", previewInsightsState(InsightsMetric.Unlocks))

    @Test fun emptyDay() =
        shot("InsightsEmptyDay", previewInsightsState(InsightsMetric.ScreenTime, offset = -3, empty = true))

    @Test
    fun nextIsDisabledOnTodayAndMetricTabsSelect() {
        val picked = mutableListOf<InsightsMetric>()
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                StayFocusedTheme {
                    InsightsContent(previewInsightsState(InsightsMetric.ScreenTime), {}, {}, { picked += it })
                }
            }
        }
        composeRule.onNodeWithContentDescription("Next day").assertIsNotEnabled()
        composeRule.onNodeWithText("Unlocks").performClick()
        assertEquals(listOf(InsightsMetric.Unlocks), picked)
    }
}
