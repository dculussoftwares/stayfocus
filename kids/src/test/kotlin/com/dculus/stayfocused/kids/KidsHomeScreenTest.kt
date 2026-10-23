package com.dculus.stayfocused.kids

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dculus.stayfocused.core.sync.KidsRule
import com.dculus.stayfocused.core.sync.KidsRuleType
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
class KidsHomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        state: KidsHomeUiState,
        onUnlink: () -> Unit = {},
        onConfirm: () -> Unit = {},
        onKeep: () -> Unit = {},
    ) = composeRule.setContent {
        StayFocusedTheme { KidsHomeScreen(state, onUnlink, onConfirm, onKeep) }
    }

    private val ready =
        KidsHomeUiState(
            parentName = "Asha",
            todayMins = 72,
            rules =
                listOf(
                    KidsRule("1", "Instagram", KidsRuleType.Limit, limitMins = 30),
                    KidsRule("2", "Games", KidsRuleType.Schedule, start = "08:00", end = "15:00", days = 0b0011111),
                ),
        )

    @Test
    fun showsTheNoticeTodayAndTheRules() {
        show(ready)
        composeRule.onNodeWithText("This phone is managed by Asha").assertIsDisplayed()
        composeRule.onNodeWithText("Screen time and app use are shared with Asha.").assertIsDisplayed()
        composeRule.onNodeWithTag(KIDS_HOME_TODAY_TAG).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("1h 12m").assertIsDisplayed()
        composeRule.onNodeWithText("RULES SET BY ASHA").assertIsDisplayed()
        composeRule.onNodeWithText("Instagram").assertIsDisplayed()
        composeRule.onNodeWithText("30 min a day").assertIsDisplayed()
        composeRule.onNodeWithText("Mon–Fri · 08:00–15:00").assertIsDisplayed()
        composeRule.onNodeWithText("↑ PERSISTENT NOTIFICATION · REQUIRED BY PLAY POLICY").assertIsDisplayed()
        composeRule.onNodeWithText("Unlink from Asha").assertIsDisplayed()
    }

    @Test
    fun withoutRulesItSaysSo() {
        show(ready.copy(rules = emptyList(), todayMins = 48))
        composeRule.onNodeWithText("Asha has not set any rules yet.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("48m").assertIsDisplayed()
    }

    @Test
    fun unlinkAsksBeforeAnythingHappens() {
        var confirmed = 0
        var kept = 0
        show(ready.copy(confirming = true), onConfirm = { confirmed++ }, onKeep = { kept++ })
        composeRule.onNodeWithText("Unlink from Asha?").assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "Asha will be told. Blocking and sharing screen time stop on this phone.",
            ).assertIsDisplayed()
        composeRule.onNodeWithTag(KIDS_HOME_KEEP_TAG).performClick()
        assertEquals(0 to 1, confirmed to kept)
        composeRule.onNodeWithTag(KIDS_HOME_CONFIRM_TAG).performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun theUnlinkButtonAsksToConfirm() {
        var asked = 0
        show(ready, onUnlink = { asked++ })
        composeRule.onNodeWithTag(KIDS_HOME_UNLINK_TAG).performClick()
        assertEquals(1, asked)
    }

    @Test
    fun showsWhyUnlinkingFailed() {
        show(ready.copy(error = UnlinkError.Network))
        composeRule.onNodeWithTag(KIDS_HOME_ERROR_TAG).assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "Could not reach the server. Check the connection and try again.",
            ).assertIsDisplayed()
    }
}
