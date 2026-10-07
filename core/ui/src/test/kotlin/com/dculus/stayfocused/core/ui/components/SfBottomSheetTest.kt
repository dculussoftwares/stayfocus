package com.dculus.stayfocused.core.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
class SfBottomSheetTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var visible by mutableStateOf(true)
    private var dismissals = 0

    private fun setSheet() {
        composeRule.setContent {
            StayFocusedTheme {
                SfBottomSheet(
                    visible = visible,
                    onDismiss = {
                        dismissals++
                        visible = false
                    },
                ) { MonoLabel("SHEET BODY") }
            }
        }
    }

    @Test
    fun scrimTapDismisses() {
        setSheet()
        composeRule.onNodeWithText("SHEET BODY").assertExists()
        composeRule.onNodeWithTag(SF_SHEET_SCRIM_TAG).performClick()
        assertEquals(1, dismissals)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("SHEET BODY").assertDoesNotExist()
    }

    @Test
    fun panelTapDoesNotDismiss() {
        setSheet()
        composeRule.onNodeWithText("SHEET BODY").performClick()
        assertEquals(0, dismissals)
    }

    @Test
    fun backPressDismisses() {
        setSheet()
        Espresso.pressBack()
        assertEquals(1, dismissals)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("SHEET BODY").assertDoesNotExist()
    }
}
