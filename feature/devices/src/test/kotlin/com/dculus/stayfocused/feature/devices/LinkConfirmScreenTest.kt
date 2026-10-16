package com.dculus.stayfocused.feature.devices

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dculus.stayfocused.core.sync.LinkFailure
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
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
class LinkConfirmScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        state: LinkConfirmState,
        name: String = "Pixel 7",
        onRetry: () -> Unit = {},
    ) = composeRule.setContent {
        StayFocusedTheme { LinkConfirmScreen(state, name, onNameChange = {}, onBack = {}, onRetry = onRetry) }
    }

    @Test
    fun waitingShowsTheCodeInGroupsTheNameFieldAndTheNote() {
        show(LinkConfirmState.Waiting("482913"))
        composeRule.onNodeWithText("Do the codes match?").assertIsDisplayed()
        composeRule.onNodeWithText("482 913").assertIsDisplayed()
        composeRule.onNodeWithTag(LINK_CONFIRM_NAME_TAG).assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "Linking finishes when your child taps Allow. They can unlink anytime, and you'll be told if they do.",
            ).assertIsDisplayed()
        composeRule.onNodeWithTag(LINK_CONFIRM_WAITING_TAG).assertIsDisplayed()
    }

    @Test
    fun anExpiredCodeSaysSoAndOffersNoRetry() {
        show(LinkConfirmState.Failed(LinkFailure.Expired))
        composeRule.onNodeWithText("That code ran out").assertIsDisplayed()
        composeRule.onNodeWithTag(LINK_CONFIRM_RETRY_TAG).assertDoesNotExist()
    }

    @Test
    fun noConnectionOffersToTryAgain() {
        var retries = 0
        show(LinkConfirmState.Failed(LinkFailure.Network), onRetry = { retries++ })
        composeRule.onNodeWithTag(LINK_CONFIRM_RETRY_TAG).performClick()
        assertEquals(1, retries)
    }

    @Test
    fun aDeclinedLinkIsExplained() {
        show(LinkConfirmState.Declined)
        composeRule.onNodeWithText("Not linked").assertIsDisplayed()
    }
}
