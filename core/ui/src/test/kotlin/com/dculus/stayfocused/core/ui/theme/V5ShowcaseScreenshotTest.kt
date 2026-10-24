package com.dculus.stayfocused.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    @Config(qualifiers = "w411dp-h1100dp-mdpi")
    fun v5ShowcaseGradients() {
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                V5Screen(darkContent = false) { V5ShowcaseGradients() }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/v5/V5ShowcaseGradients.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h520dp-mdpi")
    fun v5ShowcaseComponents() {
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                V5Screen(darkContent = false) { V5ShowcaseComponents() }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/v5/V5ShowcaseComponents.png")
    }

    @Test
    fun upperRadialLayerIsDrawnOverTheBase() {
        composeRule.setContent {
            Box(Modifier.size(200.dp, 400.dp).background(V5Gradients.brand.adaptive))
        }
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        // The brand highlight is centred at 75 % x, 15 % y, so it lightens there but not in the far corner.
        val highlighted = Color(bitmap.getPixel(bitmap.width * 3 / 4, bitmap.height * 15 / 100)).green
        val corner = Color(bitmap.getPixel(1, bitmap.height - 2)).green
        assertTrue("highlight $highlighted vs corner $corner", highlighted - corner > 0.1f)
    }

    @Test
    fun grainRendersIdenticallyTwice() {
        composeRule.setContent {
            StayFocusedTheme {
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
