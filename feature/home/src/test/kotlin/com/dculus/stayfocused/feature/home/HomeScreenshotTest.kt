package com.dculus.stayfocused.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class HomeScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) { StayFocusedTheme { content() } }
        }
    }

    @Test fun sheetContent() {
        show {
            Box(Modifier.width(360.dp).background(StayFocusedTheme.colors.surfaceRaised).padding(18.dp)) {
                BreakSheetContent(mins = 30, onMinsChange = {}, onStart = {})
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/BreakSheet.png")
    }

    @Test fun startButtonStaysReachableOnAShortScreen() {
        val started = mutableListOf<Int>()
        show {
            Box(Modifier.height(240.dp).width(360.dp)) {
                BreakSheetContent(mins = 30, onMinsChange = {}, onStart = { started += 30 })
            }
        }
        composeRule.onNodeWithTag(BREAK_START_TAG).performScrollTo().performClick()
        assertEquals(listOf(30), started)
    }
}
