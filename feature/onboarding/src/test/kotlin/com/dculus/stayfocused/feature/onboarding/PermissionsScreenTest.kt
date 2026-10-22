package com.dculus.stayfocused.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.dculus.stayfocused.core.model.Permissions
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
            CompositionLocalProvider(LocalInspectionMode provides true) { content() }
        }

    private fun screen(
        permissions: Permissions,
        onAllow: (PermissionRow) -> Unit = {},
        onContinue: () -> Unit = {},
    ) = show { PermissionsScreen(permissions, onAllow, onContinue) }

    @Test
    fun twoOfFour() {
        screen(Permissions(usage = true, accessibility = false, overlay = true, notifications = false))
        composeRule.onNodeWithText("2 of 4 allowed").assertIsDisplayed()
        composeRule.onNodeWithTag(PERMS_CONTINUE_TAG).assertIsNotEnabled()
        composeRule.onNodeWithText("Continue for now").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/Permissions.png")
    }

    @Test
    fun allSet() {
        screen(Permissions(usage = true, accessibility = true, overlay = true, notifications = true))
        composeRule.onNodeWithText("Start using Stay Focused").assertIsDisplayed()
        composeRule.onNodeWithTag(PERMS_CONTINUE_TAG).assertIsEnabled()
        composeRule.onAllNodesWithText("Continue for now").assertCountEquals(0)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/PermissionsAllSet.png")
    }

    @Test
    fun rowsShowTheirReasons() {
        screen(Permissions(usage = false, accessibility = false, overlay = false, notifications = false))
        composeRule.onNodeWithText("Four switches,\nthen you're set").assertIsDisplayed()
        composeRule
            .onNodeWithText("Android needs these to block apps. Your data stays on this phone.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("0 of 4 allowed").assertIsDisplayed()
        listOf(
            "Usage access" to "See how long each app is used",
            "Accessibility" to "Spot a blocked app the moment it opens",
            "Display over other apps" to "Show the block screen on top",
            "Notifications" to "Tell you when a block starts or ends",
        ).forEach { (title, why) ->
            composeRule.onNodeWithText(title).assertIsDisplayed()
            composeRule.onNodeWithText(why).assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Allow $title").assertIsDisplayed()
        }
    }

    @Test
    fun grantedRowsAnnounceThemselves() {
        screen(Permissions(usage = true, accessibility = false, overlay = false, notifications = false))
        composeRule.onNodeWithContentDescription("Usage access allowed").assertIsDisplayed()
    }

    @Test
    fun largeFontKeepsEverythingReachable() {
        composeRule.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                androidx.compose.ui.platform.LocalDensity provides
                    androidx.compose.ui.unit
                        .Density(density.density, fontScale = 1.3f),
            ) {
                PermissionsScreen(
                    Permissions(usage = false, accessibility = false, overlay = false, notifications = false),
                    {},
                    {},
                )
            }
        }
        composeRule.onNodeWithTag(permsAllowTag(PermissionRow.Notifications)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(PERMS_SKIP_TAG).performScrollTo().assertIsDisplayed()
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
    fun continueForNowLeavesWithFewerThanFour() {
        var continued = 0
        screen(Permissions(usage = false, accessibility = false, overlay = false, notifications = false), onContinue = {
            continued++
        })
        composeRule.onNodeWithTag(PERMS_CONTINUE_TAG).performClick()
        assertEquals(0, continued)
        composeRule.onNodeWithTag(PERMS_SKIP_TAG).performClick()
        assertEquals(1, continued)
    }

    @Test
    fun startUsingFinishesWithAllFour() {
        var continued = 0
        screen(Permissions(usage = true, accessibility = true, overlay = true, notifications = true), onContinue = {
            continued++
        })
        composeRule.onNodeWithTag(PERMS_CONTINUE_TAG).performClick()
        assertEquals(1, continued)
    }

    @Test
    fun disclosureNoThanksNeverAgrees() {
        var agreed = 0
        var dismissed = 0
        show {
            PhoneAccessibilityDisclosure(
                visible = true,
                onAgree = { agreed++ },
                onDismiss = { dismissed++ },
                onPrivacyPolicy = {},
            )
        }
        composeRule.onNodeWithTag(PERMS_DISCLOSURE_NO_TAG).performClick()
        assertEquals(0, agreed)
        assertEquals(1, dismissed)
    }

    @Test
    fun disclosureShowsTheFinalCopyAndAgreeReports() {
        var agreed = 0
        show {
            PhoneAccessibilityDisclosure(
                visible = true,
                onAgree = { agreed++ },
                onDismiss = {},
                onPrivacyPolicy = {},
            )
        }
        listOf(
            "Accessibility access",
            "What it reads",
            "Which app is on screen and the address of websites you open.",
            "Why",
            "To block an app or site the moment it opens.",
            "Never",
            "Your messages, passwords or typing. Nothing leaves this phone.",
            "Agree and open Settings",
            "No thanks",
            "Turn it off anytime in Android Settings.",
            "Privacy policy",
        ).forEach { composeRule.onNodeWithText(it).assertExists() }
        composeRule.onNodeWithTag(PERMS_DISCLOSURE_AGREE_TAG).performClick()
        assertEquals(1, agreed)
    }
}
