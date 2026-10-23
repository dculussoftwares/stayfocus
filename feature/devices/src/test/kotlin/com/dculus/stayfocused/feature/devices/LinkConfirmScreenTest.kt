package com.dculus.stayfocused.feature.devices

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.dculus.stayfocused.core.sync.LinkFailure
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
class LinkConfirmScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        state: LinkConfirmState,
        name: String = "Aarav's phone",
        onRetry: () -> Unit = {},
        onBack: () -> Unit = {},
    ) = composeRule.setContent {
        LinkConfirmScreen(state, name, onNameChange = {}, onBack = onBack, onRetry = onRetry)
    }

    @Test
    fun waitingShowsTheCodeInGroupsTheNameFieldAndTheNote() {
        show(LinkConfirmState.Waiting("482913"))
        composeRule.onNodeWithText("Do the codes\nmatch?").assertIsDisplayed()
        composeRule.onNodeWithText("Your child's phone shows the same six digits.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("4 8 2 9 1 3").assertIsDisplayed()
        composeRule.onNodeWithText("Name this phone").assertIsDisplayed()
        composeRule.onNodeWithTag(LINK_CONFIRM_NAME_TAG).assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "Your child sees that you manage this phone and can unlink anytime. You get notified if they do.",
            ).assertIsDisplayed()
        composeRule.onNodeWithText("Codes match, link it").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/LinkConfirm.png")
    }

    @Test
    fun tappingCodesMatchShowsWaitingForTheChild() {
        show(LinkConfirmState.Waiting("482913"))
        composeRule.onNodeWithTag(LINK_CONFIRM_LINK_TAG).performClick()
        composeRule.onNodeWithTag(LINK_CONFIRM_WAITING_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Waiting for your child to tap Allow").assertIsDisplayed()
    }

    @Test
    fun backIsLabelled() {
        var backs = 0
        show(LinkConfirmState.Waiting("482913"), onBack = { backs++ })
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
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
