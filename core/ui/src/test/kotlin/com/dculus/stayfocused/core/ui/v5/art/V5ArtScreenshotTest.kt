package com.dculus.stayfocused.core.ui.v5.art

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.dculus.stayfocused.core.ui.v5.icons.V5IconsPreview
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Roborazzi baselines (static frames) for the v5 icon sheet and animated art. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class V5ArtScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(
        path: String,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent { CompositionLocalProvider(LocalInspectionMode provides true) { content() } }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/v5/$path.png")
    }

    @Test
    @Config(qualifiers = "w520dp-h260dp-xxhdpi")
    fun iconSheet() = capture("icons/V5IconsSheet") { V5IconsPreview() }

    @Test
    @Config(qualifiers = "w300dp-h190dp-xxhdpi")
    fun breakArt() = capture("art/BreakArt") { BreakArtPreview() }

    @Test
    @Config(qualifiers = "w300dp-h160dp-xxhdpi")
    fun linkArt() = capture("art/LinkArt") { LinkArtPreview() }

    @Test
    @Config(qualifiers = "w390dp-h360dp-xxhdpi")
    fun scanFrame() = capture("art/ScanFrame") { ScanFramePreview() }

    @Test
    @Config(qualifiers = "w200dp-h60dp-xxhdpi")
    fun indicators() = capture("art/SpinnerAndPulse") { IndicatorsPreview() }
}
