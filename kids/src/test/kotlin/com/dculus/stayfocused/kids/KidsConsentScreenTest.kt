package com.dculus.stayfocused.kids

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
@Config(sdk = [34], qualifiers = "w360dp-h1200dp-xxhdpi")
class KidsConsentScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        state: KidsConsentUiState,
        onAllow: () -> Unit = {},
        onCancel: () -> Unit = {},
        onShowNewCode: () -> Unit = {},
    ) = composeRule.setContent {
        StayFocusedTheme { KidsConsentScreen(state, onAllow, onCancel, onShowNewCode) }
    }

    private val ready = KidsConsentUiState(code = "482913", parentName = "Asha")

    @Test
    fun showsTheCodeTheParentCanDoListAndTheNote() {
        show(ready)
        composeRule.onNodeWithText("Let Asha manage this phone?").assertIsDisplayed()
        composeRule.onNodeWithText("482 913").assertIsDisplayed()
        composeRule.onNodeWithText("Lock and unlock apps").assertIsDisplayed()
        composeRule.onNodeWithText("Answer your requests for more time").assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "A notification always shows while this phone is managed. " +
                    "You can unlink anytime, and Asha will be told.",
            ).assertIsDisplayed()
    }

    @Test
    fun aParentWithoutANameIsCalledYourParent() {
        show(ready.copy(parentName = null))
        composeRule.onNodeWithText("Let your parent manage this phone?").assertIsDisplayed()
    }

    @Test
    fun allowAndCancelReachTheViewModel() {
        var allowed = 0
        var cancelled = 0
        show(ready, onAllow = { allowed++ }, onCancel = { cancelled++ })
        composeRule.onNodeWithTag(KIDS_CONSENT_ALLOW_TAG).performClick()
        composeRule.onNodeWithTag(KIDS_CONSENT_CANCEL_TAG).performClick()
        assertEquals(1, allowed)
        assertEquals(1, cancelled)
    }

    @Test
    fun nothingCanBeAllowedBeforeTheCodeArrives() {
        show(KidsConsentUiState())
        composeRule.onNodeWithTag(KIDS_CONSENT_ALLOW_TAG).assertDoesNotExist()
    }

    @Test
    fun anExpiredCodeOffersANewOneInsteadOfAllow() {
        var newCodes = 0
        show(ready.copy(error = ConsentError.Expired), onShowNewCode = { newCodes++ })
        composeRule.onNodeWithTag(KIDS_CONSENT_ALLOW_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(KIDS_CONSENT_NEW_CODE_TAG).performClick()
        assertEquals(1, newCodes)
    }
}
