package com.dculus.stayfocused.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DotMatrixTextTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun intrinsicSizeIsGlyphWidthTimesSevenDots() {
        composeRule.setContent {
            StayFocusedTheme {
                Column {
                    DotMatrixText("23", "23", 5.dp, Color.White, Modifier.testTag("two"))
                    DotMatrixText("2:33", "2:33", 8.dp, Color.White, Modifier.testTag("clock"))
                }
            }
        }
        composeRule.onNodeWithTag("two").assertWidthIsEqualTo(55.dp).assertHeightIsEqualTo(35.dp)
        composeRule.onNodeWithTag("clock").assertWidthIsEqualTo(152.dp).assertHeightIsEqualTo(56.dp)
    }

    @Test
    fun countdownKeepsItsWidthWhenDigitsChange() {
        composeRule.setContent {
            StayFocusedTheme {
                Column {
                    DotMatrixText("14:52", "a", 5.dp, Color.White, Modifier.testTag("a"))
                    DotMatrixText("11:11", "b", 5.dp, Color.White, Modifier.testTag("b"))
                }
            }
        }
        composeRule.onNodeWithTag("a").assertWidthIsEqualTo(125.dp)
        composeRule.onNodeWithTag("b").assertWidthIsEqualTo(125.dp)
    }

    @Test
    fun announcesTheReadableDescriptionOnly() {
        composeRule.setContent {
            StayFocusedTheme { DotMatrixText("2:33", "2 hours 33 minutes", 6.dp, Color.White) }
        }
        composeRule.onNodeWithContentDescription("2 hours 33 minutes").assertExists()
    }

    @Test
    fun thinNumeralsHideTheirTextFromAccessibility() {
        composeRule.setContent {
            StayFocusedTheme {
                DotMatrixText(
                    "2:33",
                    "2 hours 33 minutes",
                    6.dp,
                    Color.White,
                    numerals = Numerals.Thin,
                )
            }
        }
        composeRule.onNodeWithContentDescription("2 hours 33 minutes").assertExists()
        composeRule.onAllNodesWithText("2:33", useUnmergedTree = true).assertCountEquals(0)
    }
}
