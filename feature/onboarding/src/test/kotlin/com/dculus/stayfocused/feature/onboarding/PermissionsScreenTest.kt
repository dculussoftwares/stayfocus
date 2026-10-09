package com.dculus.stayfocused.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.dculus.stayfocused.core.model.Permissions
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
class PermissionsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(content: @Composable () -> Unit) =
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) { StayFocusedTheme { content() } }
        }

    private fun screen(
        permissions: Permissions,
        onAllow: (PermissionRow) -> Unit = {},
        onContinue: () -> Unit = {},
    ) = show { PermissionsScreen(permissions, onAllow, onContinue) }

    @Test
    fun twoOfFour() {
        screen(Permissions(usage = true, accessibility = false, overlay = true, notifications = false))
        composeRule.onNodeWithText("2/4").assertIsDisplayed()
        composeRule.onNodeWithText("Continue for now").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/PermissionsTwoOfFour.png")
    }

    @Test
    fun allSet() {
        screen(Permissions(usage = true, accessibility = true, overlay = true, notifications = true))
        composeRule.onNodeWithText("4/4").assertIsDisplayed()
        composeRule.onNodeWithText("All set · Continue").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/PermissionsAllSet.png")
    }

    @Test
    fun rowsShowTheirReasons() {
        screen(Permissions(usage = false, accessibility = false, overlay = false, notifications = false))
        listOf(
            "Usage access" to "Shows screen time and knows when limits are reached.",
            "Accessibility" to "Notices when a blocked app opens.",
            "Display over apps" to "Shows the block screen on top.",
            "Notifications" to "Breaks, limits and requests.",
        ).forEach { (title, why) ->
            composeRule.onNodeWithText(title).assertIsDisplayed()
            composeRule.onNodeWithText(why).assertIsDisplayed()
        }
    }

    @Test
    fun allowReportsTheRowAndGrantedRowsAreInert() {
        val tapped = mutableListOf<PermissionRow>()
        screen(
            Permissions(usage = true, accessibility = false, overlay = false, notifications = false),
            onAllow = { tapped += it },
        )
        composeRule.onNodeWithTag(permsAllowTag(PermissionRow.Usage)).performClick()
        composeRule.onNodeWithTag(permsAllowTag(PermissionRow.Overlay)).performClick()
        assertEquals(listOf(PermissionRow.Overlay), tapped)
    }

    @Test
    fun continueInvokesCallback() {
        var continued = 0
        screen(Permissions(usage = false, accessibility = false, overlay = false, notifications = false), onContinue = {
            continued++
        })
        composeRule.onNodeWithTag(PERMS_CONTINUE_TAG).performClick()
        assertEquals(1, continued)
    }

    @Test
    fun disclosureNoThanksNeverAgrees() {
        var agreed = 0
        var dismissed = 0
        show { ProvisionalAccessibilityDisclosure(visible = true, onAgree = { agreed++ }, onDismiss = { dismissed++ }) }
        composeRule.onNodeWithTag(PERMS_DISCLOSURE_NO_TAG).performClick()
        assertEquals(0, agreed)
        assertEquals(1, dismissed)
    }
}
