package com.dculus.stayfocused.feature.account

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
@Config(sdk = [34], qualifiers = "w360dp-h900dp-xxhdpi")
class DeleteAccountSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        state: DeleteUiState,
        onConfirm: () -> Unit = {},
        onCancel: () -> Unit = {},
        onPassword: (String) -> Unit = {},
        onGoogle: () -> Unit = {},
    ) = composeRule.setContent {
        StayFocusedTheme { DeleteAccountSheet(state, onConfirm, onCancel, onPassword, onGoogle) }
    }

    @Test
    fun confirmStepAsksThenDeletes() {
        var confirmed = 0
        show(DeleteUiState(step = DeleteStep.Confirm), onConfirm = { confirmed++ })
        composeRule.onNodeWithText("Delete account and all data?").assertIsDisplayed()
        composeRule.onNodeWithTag(DELETE_CONFIRM_TAG).performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun cancelClosesWithoutDeleting() {
        var cancelled = 0
        var confirmed = 0
        show(DeleteUiState(step = DeleteStep.Confirm), onConfirm = { confirmed++ }, onCancel = { cancelled++ })
        composeRule.onNodeWithTag(DELETE_CANCEL_TAG).performClick()
        assertEquals(1, cancelled)
        assertEquals(0, confirmed)
    }

    @Test
    fun reauthStepOffersGoogleForGoogleAccounts() {
        var google = 0
        show(DeleteUiState(step = DeleteStep.Reauth, usesPassword = false), onGoogle = { google++ })
        composeRule.onNodeWithText("Confirm it's you").assertIsDisplayed()
        composeRule.onNodeWithTag(DELETE_GOOGLE_TAG).performClick()
        assertEquals(1, google)
    }

    @Test
    fun reauthStepShowsTheWrongPasswordError() {
        show(DeleteUiState(step = DeleteStep.Reauth, usesPassword = true, error = DeleteError.WrongPassword))
        composeRule.onNodeWithText("Wrong password. Try again.").assertIsDisplayed()
    }
}
