package com.dculus.stayfocused.core.ui.components

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

/** Roborazzi baseline for the navigation placeholder screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
class NavPlaceholderScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun navPlaceholderScreen() {
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) { NavPlaceholderScreenPreview() }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/components/NavPlaceholderScreen.png")
    }
}
