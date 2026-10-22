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
@Config(sdk = [34], qualifiers = "w360dp-h780dp-hdpi")
class InsightsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun shot(
        name: String,
        state: InsightsUiState,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) { InsightsScreen(state, {}) }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun day() = shot("InsightsDay", previewInsightsState(InsightsRange.Day))

    @Test fun week() = shot("InsightsWeek", previewInsightsState(InsightsRange.Week))

    @Test fun opens() = shot("InsightsOpens", previewInsightsState(InsightsRange.Day, InsightsMetric.Opens))

    @Test fun emptyDay() = shot("InsightsEmptyDay", previewInsightsState(InsightsRange.Day, offset = -3, empty = true))

    @Test
    fun nextIsDisabledOnTodayAndRangeAndMetricSelect() {
        val events = mutableListOf<InsightsEvent>()
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                InsightsScreen(state = previewInsightsState(InsightsRange.Day), onEvent = { events += it })
            }
        }
        composeRule.onNodeWithContentDescription("Next day").assertIsNotEnabled()
        composeRule.onNodeWithText("Week").performClick()
        composeRule.onNodeWithText("Opens").performClick()
        assertEquals(
            listOf(
                InsightsEvent.SelectRange(InsightsRange.Week),
                InsightsEvent.SelectMetric(InsightsMetric.Opens),
            ),
            events,
        )
    }
}
