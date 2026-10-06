package com.dculus.stayfocused.core.ui.theme

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1700dp-xxhdpi")
class ThemeShowcaseScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun themeShowcase() {
        composeRule.setContent { StayFocusedTheme { ThemeShowcase() } }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/ThemeShowcase.png")
    }
}
