package com.dculus.stayfocused.core.ui.v5.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class V5ShowcaseScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w411dp-h2700dp-xhdpi")
    fun v5ShowcaseTokens() {
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                V5Screen(darkContent = false) { V5ShowcaseTokens() }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/v5/V5ShowcaseTokens.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h900dp-mdpi")
    fun v5ShowcaseGradients() {
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                V5Screen(darkContent = false) { V5ShowcaseGradients() }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/v5/V5ShowcaseGradients.png")
    }

    @Test
    fun grainRendersIdenticallyTwice() {
        composeRule.setContent {
            V5Theme {
                Box(Modifier.size(200.dp).glow(V5Gradients.welcome))
            }
        }

        fun pixels(): IntArray {
            val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
            return IntArray(bitmap.width * bitmap.height).also {
                bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            }
        }
        val first = pixels()
        composeRule.waitForIdle()
        val second = pixels()
        assertTrue(first.contentEquals(second))
    }
}
