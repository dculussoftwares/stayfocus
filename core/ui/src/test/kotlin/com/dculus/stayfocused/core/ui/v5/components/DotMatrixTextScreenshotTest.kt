package com.dculus.stayfocused.core.ui.v5.components

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

/** Roborazzi baselines for the dot-matrix numerals, at every size the v5 screens use. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h760dp-xhdpi")
class DotMatrixTextScreenshotTest {
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
    fun dots() = capture("DotMatrixText") { DotMatrixTextPreview() }

    @Test
    fun thin() = capture("DotMatrixTextThin") { DotMatrixTextThinPreview() }
}
