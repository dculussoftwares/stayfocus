package com.dculus.stayfocused.feature.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.dculus.stayfocused.core.blocking.engine.EngineHealth
import com.dculus.stayfocused.core.model.Permissions
import com.dculus.stayfocused.core.sync.AuthUser
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
@Config(sdk = [34], qualifiers = "w360dp-h900dp-xxhdpi")
class AccountScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val allOn =
        Permissions(
            usage = true,
            accessibility = true,
            overlay = true,
            notifications = true,
            batteryUnrestricted = true,
        )
    private val user = AuthUser("u", "alex@example.com", "Alex")

    private fun show(
        state: AccountUiState,
        onFix: (HealthRow) -> Unit = {},
        onSignIn: () -> Unit = {},
        onSignOut: () -> Unit = {},
        onOpenUrl: (String) -> Unit = {},
        onDeleteAccount: () -> Unit = {},
    ) = composeRule.setContent {
        Themed { AccountScreen(state, "1.0.0", {}, onSignIn, onFix, onOpenUrl, {}, onSignOut, onDeleteAccount) }
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalInspectionMode provides true) { StayFocusedTheme { content() } }
    }

    @Test
    fun signedIn() {
        show(AccountUiState(user, allOn, EngineHealth(serviceBound = true)))
        composeRule.onNodeWithText("Alex").assertIsDisplayed()
        composeRule.onNodeWithText("alex@example.com").assertIsDisplayed()
        composeRule.onNodeWithTag(ACCOUNT_SIGN_OUT_TAG).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(ACCOUNT_DELETE_TAG).performScrollTo().assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/AccountSignedIn.png")
    }

    @Test
    fun skipped() {
        var signIn = 0
        show(AccountUiState(null, allOn, EngineHealth(serviceBound = true)), onSignIn = { signIn++ })
        composeRule.onNodeWithText("Not signed in").assertIsDisplayed()
        composeRule.onNodeWithTag(ACCOUNT_SIGN_IN_TAG).performClick()
        assertEquals(1, signIn)
        composeRule.onNodeWithTag(ACCOUNT_SIGN_OUT_TAG).assertDoesNotExist()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/AccountSkipped.png")
    }

    @Test
    fun permissionMissing() {
        val fixed = mutableListOf<HealthRow>()
        show(
            AccountUiState(user, allOn.copy(accessibility = false), EngineHealth(serviceBound = false)),
            onFix = { fixed += it },
        )
        composeRule.onNodeWithTag(healthTag(HealthRow.Usage)).performClick()
        composeRule.onNodeWithTag(healthTag(HealthRow.Accessibility)).performClick()
        assertEquals(listOf(HealthRow.Accessibility), fixed)
        composeRule.onNodeWithText("STOPPED").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/AccountPermissionMissing.png")
    }

    @Test
    fun linksAndSignOutReport() {
        val urls = mutableListOf<String>()
        var out = 0
        var deleted = 0
        show(
            AccountUiState(user, allOn),
            onOpenUrl = { urls += it },
            onSignOut = { out++ },
            onDeleteAccount = { deleted++ },
        )
        composeRule.onNodeWithText("Source on GitHub").performScrollTo().performClick()
        composeRule.onNodeWithText("Privacy policy").performScrollTo().performClick()
        composeRule.onNodeWithTag(ACCOUNT_SIGN_OUT_TAG).performScrollTo().performClick()
        composeRule.onNodeWithTag(ACCOUNT_DELETE_TAG).performScrollTo().performClick()
        assertEquals(listOf(GITHUB_URL, PRIVACY_URL), urls)
        assertEquals(1, out)
        assertEquals(1, deleted)
    }
}
