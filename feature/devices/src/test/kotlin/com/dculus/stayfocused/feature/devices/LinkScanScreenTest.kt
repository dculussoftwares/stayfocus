package com.dculus.stayfocused.feature.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class LinkScanScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        access: CameraAccess,
        onBack: () -> Unit = {},
        onAllow: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
    ) = composeRule.setContent {
        LinkScanScreen(
            access = access,
            onBack = onBack,
            onAllow = onAllow,
            onOpenSettings = onOpenSettings,
            onCodes = {},
            onCameraUnavailable = {},
            // CameraX does not run under Robolectric: a flat stand-in fills the viewport.
            preview = { Box(it.background(Color(0xFF1B1F26))) },
        )
    }

    @Test
    fun grantedShowsTheCopyTheFrameAndTheStatusLine() {
        show(CameraAccess.Granted)
        composeRule.onNodeWithText("Scan their code").assertIsDisplayed()
        composeRule
            .onNodeWithText("Open Stay Focused Kids on your child's phone and point your camera at the code.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(LINK_SCAN_VIEWFINDER_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Looking for a code…").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/LinkScan.png")
    }

    @Test
    fun backIsLabelled() {
        var backs = 0
        show(CameraAccess.Granted, onBack = { backs++ })
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun rationaleOffersToAllowTheCamera() {
        var allowed = 0
        show(CameraAccess.NeedsRationale, onAllow = { allowed++ })
        composeRule.onNodeWithText("Allow the camera").assertIsDisplayed()
        composeRule.onNodeWithTag(LINK_SCAN_ALLOW_TAG).performClick()
        assertEquals(1, allowed)
    }

    @Test
    fun deniedOffersSettings() {
        var opened = 0
        show(CameraAccess.Denied, onOpenSettings = { opened++ })
        composeRule.onNodeWithTag(LINK_SCAN_SETTINGS_TAG).performClick()
        assertEquals(1, opened)
    }

    @Test
    fun unavailableHasNoButton() {
        show(CameraAccess.Unavailable)
        composeRule.onNodeWithText("Camera not available").assertIsDisplayed()
        composeRule.onNodeWithTag(LINK_SCAN_ALLOW_TAG).assertDoesNotExist()
    }
}
