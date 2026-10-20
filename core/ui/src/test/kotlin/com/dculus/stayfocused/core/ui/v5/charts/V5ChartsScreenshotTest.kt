package com.dculus.stayfocused.core.ui.v5.charts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Roborazzi baselines for the v5 charts with the prototype's sample data (`ME_H`, `KID_H`, `WEEK`). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class V5ChartsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(
        name: String,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent { CompositionLocalProvider(LocalInspectionMode provides true) { content() } }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/v5/charts/$name.png")
    }

    @Test
    @Config(qualifiers = "w390dp-h260dp-xxhdpi")
    fun homeDotGrid() = capture("HomeDotGrid") { HomeDotGridPreview() }

    @Test
    @Config(qualifiers = "w390dp-h400dp-xxhdpi")
    fun childHourTicks() = capture("ChildHourTicks") { ChildHourTicksPreview() }

    @Test
    @Config(qualifiers = "w390dp-h400dp-xxhdpi")
    fun insightsDay() = capture("InsightsDay") { InsightsDayChartPreview() }

    @Test
    @Config(qualifiers = "w390dp-h400dp-xxhdpi")
    fun insightsWeek() = capture("InsightsWeek") { InsightsWeekChartPreview() }

    @Test
    @Config(qualifiers = "w390dp-h420dp-xxhdpi")
    fun usageRows() = capture("UsageRows") { UsageRowsPreview() }

    @Test
    @Config(qualifiers = "w390dp-h1060dp-xhdpi")
    fun edgeCases() = capture("EdgeCases") { ChartEdgeCasesPreview() }
}
