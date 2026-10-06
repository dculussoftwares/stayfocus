package com.dculus.stayfocused.core.ui.icon

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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w220dp-h72dp-xxhdpi")
class AppIconScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** Letter-tile fallback (inspection mode never hits PackageManager). */
    @Test
    fun letterTiles() {
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) { AppIconPreview() }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/AppIconTiles.png")
    }
}
