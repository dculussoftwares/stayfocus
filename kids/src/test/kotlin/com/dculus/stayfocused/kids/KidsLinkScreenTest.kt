package com.dculus.stayfocused.kids

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1200dp-xxhdpi")
class KidsLinkScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsTheCopyTheQrAndTheCountdown() {
        composeRule.setContent { KidsLinkScreen(KidsLinkState.Waiting("stayfocused://link?t=abc", "4:52"), {}) }
        composeRule.onNodeWithText("Stay Focused Kids").assertIsDisplayed()
        composeRule.onNodeWithText("Show this to\nyour parent").assertIsDisplayed()
        composeRule.onNodeWithText("They scan it from their phone.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("QR code for the parent to scan").assertIsDisplayed()
        composeRule.onNodeWithText("Code expires in 4:52").assertIsDisplayed()
    }

    @Test
    fun aFailedCodeOffersRetry() {
        var retries = 0
        composeRule.setContent { KidsLinkScreen(KidsLinkState.Failed, { retries++ }) }
        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
    }
}
