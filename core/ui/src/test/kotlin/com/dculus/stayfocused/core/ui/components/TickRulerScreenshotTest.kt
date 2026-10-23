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

/** Roborazzi baselines for the v5 `TickRuler` in both colour sets (12-… wizard card, 06-… break sheet). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TickRulerScreenshotTest {
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
    @Config(qualifiers = "w390dp-h330dp-xxhdpi")
    fun wizard() = capture("TickRulerWizard") { TickRulerWizardPreview() }

    @Test
    @Config(qualifiers = "w390dp-h300dp-xxhdpi")
    fun breakSheet() = capture("TickRulerBreakSheet") { TickRulerBreakSheetPreview() }

    @Test
    @Config(qualifiers = "w390dp-h360dp-xxhdpi")
    fun edges() = capture("TickRulerEdges") { TickRulerEdgesPreview() }
}
