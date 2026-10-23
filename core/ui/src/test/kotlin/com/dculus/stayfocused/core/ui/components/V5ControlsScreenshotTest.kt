package com.dculus.stayfocused.core.ui.components

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

/** Roborazzi baselines for every v5 control (the same `@Preview`s Android Studio shows). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class V5ControlsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(
        name: String,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent { CompositionLocalProvider(LocalInspectionMode provides true) { content() } }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/v5/components/$name.png")
    }

    @Test
    @Config(qualifiers = "w390dp-h760dp-xxhdpi")
    fun pillButtonsLight() = capture("PillButtonsLight") { PillButtonLightPreview() }

    @Test
    @Config(qualifiers = "w390dp-h480dp-xxhdpi")
    fun pillButtonsGradient() = capture("PillButtonsGradient") { PillButtonGradientPreview() }

    @Test
    @Config(qualifiers = "w390dp-h160dp-xxhdpi")
    fun roundIconButtons() = capture("RoundIconButtons") { RoundIconButtonPreview() }

    @Test
    @Config(qualifiers = "w390dp-h420dp-xxhdpi")
    fun textFields() = capture("V5TextFields") { V5TextFieldPreview() }

    @Test
    @Config(qualifiers = "w260dp-h100dp-xxhdpi")
    fun toggles() = capture("V5Toggles") { V5TogglePreview() }

    @Test
    @Config(qualifiers = "w390dp-h360dp-xxhdpi")
    fun segmentedPills() = capture("SegmentedPills") { SegmentedPillPreview() }

    @Test
    @Config(qualifiers = "w390dp-h200dp-xxhdpi")
    fun dayChips() = capture("DayChips") { DayChipsPreview() }

    @Test
    @Config(qualifiers = "w390dp-h460dp-xxhdpi")
    fun tags() = capture("Tags") { TagsPreview() }

    @Test
    @Config(qualifiers = "w390dp-h260dp-xxhdpi")
    fun appIconTiles() = capture("AppIconTiles") { AppIconTilesPreview() }
}
