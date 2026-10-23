package com.dculus.stayfocused.kids

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
class AskForMoreTimeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun askThenPickThenRequestSent() {
        val picked = mutableListOf<Int>()
        composeRule.setContent {
            var stage by remember { mutableStateOf(AskStage.None) }
            StayFocusedTheme {
                AskForMoreTime(
                    parent = "Asha",
                    stage = stage,
                    onAsk = { stage = AskStage.Pick },
                    onPick = {
                        picked += it
                        stage = AskStage.Sent
                    },
                )
            }
        }
        composeRule.onNodeWithText("Ask Asha for more time").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("HOW MUCH MORE?").assertIsDisplayed()
        composeRule.onNodeWithText("15m").assertIsDisplayed()
        composeRule.onNodeWithText("1h").assertIsDisplayed()
        composeRule.onNodeWithTag(ASK_OPTION_TAG_PREFIX + 30).performClick()
        composeRule.onNodeWithText("Request sent to Asha").assertIsDisplayed()
        composeRule.onNodeWithText("You'll get a notification when they answer.").assertIsDisplayed()
        assertEquals(listOf(30), picked)
    }

    @Test
    fun minutesAreLabelledLikeThePrototype() {
        assertEquals("15m", minutesLabel(15))
        assertEquals("1h", minutesLabel(60))
        assertEquals("1h30", minutesLabel(90))
    }
}
