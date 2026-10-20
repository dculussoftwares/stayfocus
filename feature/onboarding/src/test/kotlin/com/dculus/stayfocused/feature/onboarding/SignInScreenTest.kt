package com.dculus.stayfocused.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.dculus.stayfocused.core.ui.v5.theme.V5Screen
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
class SignInScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(content: @Composable () -> Unit) =
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) { V5Screen(darkContent = false) { content() } }
        }

    private fun screen(
        state: SignInUiState,
        onSkip: () -> Unit = {},
        onToggle: () -> Unit = {},
        onBack: () -> Unit = {},
    ) = show {
        SignInScreen(state, {}, {}, {}, onToggle, {}, {}, onSkip, onBack)
    }

    @Test
    fun signIn() {
        screen(SignInUiState(mode = AuthMode.SignIn))
        composeRule.onNodeWithText("Forgot password?").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/SignIn.png")
    }

    @Test
    fun signUp() {
        screen(SignInUiState(mode = AuthMode.CreateAccount))
        composeRule.onNodeWithText("Already have an account? Sign in").assertIsDisplayed()
        composeRule.onNodeWithText("Continue with Google").assertIsDisplayed()
        composeRule.onNodeWithText("or with email").assertIsDisplayed()
        composeRule.onNodeWithTag(SIGN_IN_FORGOT_TAG).assertDoesNotExist()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/SignUp.png")
    }

    @Test
    fun errorState() {
        screen(SignInUiState(email = "me@example.com", password = "123", error = AuthError.ShortPassword))
        composeRule.onNodeWithText("Password needs at least 6 characters.").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/SignInError.png")
    }

    @Test
    fun skipAndToggleInvokeCallbacks() {
        var skipped = 0
        var toggled = 0
        screen(SignInUiState(mode = AuthMode.SignIn), onSkip = { skipped++ }, onToggle = { toggled++ })
        composeRule.onNodeWithTag(SIGN_IN_TOGGLE_TAG).performScrollTo().performClick()
        composeRule.onNodeWithTag(SIGN_IN_SKIP_TAG).performScrollTo().performClick()
        assertEquals(1, toggled)
        assertEquals(1, skipped)
    }

    @Test
    fun createModeHidesForgotPassword() {
        screen(SignInUiState(mode = AuthMode.CreateAccount))
        composeRule.onNodeWithTag(SIGN_IN_FORGOT_TAG).assertDoesNotExist()
    }

    @Test
    fun typingReportsText() {
        var typed = ""
        show { SignInScreen(SignInUiState(), { typed = it }, {}, {}, {}, {}, {}, {}, {}) }
        composeRule.onNodeWithContentDescription("Email").performTextInput("a")
        assertEquals("a", typed)
    }

    @Test
    fun backButtonInvokesCallback() {
        var backed = 0
        screen(SignInUiState(), onBack = { backed++ })
        composeRule.onNodeWithTag(SIGN_IN_BACK_TAG).performClick()
        assertEquals(1, backed)
    }

    @Test
    fun generalFailureShowsAsItsOwnLine() {
        screen(SignInUiState(error = AuthError.Network))
        composeRule.onNodeWithTag(SIGN_IN_ERROR_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("No connection. Check your internet and try again.").assertIsDisplayed()
    }
}
