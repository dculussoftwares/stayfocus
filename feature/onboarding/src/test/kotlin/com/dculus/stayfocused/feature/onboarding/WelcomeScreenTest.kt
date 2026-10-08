package com.dculus.stayfocused.feature.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class WelcomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(content: @Composable () -> Unit) =
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) { StayFocusedTheme { content() } }
        }

    @Test
    fun welcome() {
        show { WelcomeScreen(onContinue = {}, onOpenKidsListing = {}) }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/Welcome.png")
    }

    @Test
    fun childPhoneSheet() {
        show { WelcomeScreen(onContinue = {}, onOpenKidsListing = {}, initialSheetVisible = true) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(WELCOME_OPEN_KIDS_TAG).assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/WelcomeChildSheet.png")
    }

    @Test
    fun shortViewportStaysReachable() {
        var started = 0
        show {
            Box(Modifier.size(width = 360.dp, height = 300.dp)) {
                WelcomeScreen(onContinue = { started++ }, onOpenKidsListing = {})
            }
        }
        composeRule.onNodeWithText("Take back", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(WELCOME_GET_STARTED_TAG).performScrollTo().performClick()
        assertEquals(1, started)
    }

    @Test
    fun getStartedContinues() {
        var started = 0
        show { WelcomeScreen(onContinue = { started++ }, onOpenKidsListing = {}) }
        composeRule.onNodeWithTag(WELCOME_GET_STARTED_TAG).performClick()
        assertEquals(1, started)
    }

    @Test
    fun childPhoneButtonOpensSheetWhichOpensListing() {
        var opened = 0
        show { WelcomeScreen(onContinue = {}, onOpenKidsListing = { opened++ }) }
        composeRule.onNodeWithTag(WELCOME_CHILD_PHONE_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(WELCOME_OPEN_KIDS_TAG).performClick()
        assertEquals(1, opened)
    }
}
