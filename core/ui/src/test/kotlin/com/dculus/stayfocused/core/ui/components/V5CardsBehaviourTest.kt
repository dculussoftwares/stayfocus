package com.dculus.stayfocused.core.ui.components

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.ui.theme.V5Gradients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V5CardsBehaviourTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    @Test
    fun glassBlurNeedsApi31() {
        assertFalse(glassBlurSupported(Build.VERSION_CODES.R))
        assertFalse(glassBlurSupported(Build.VERSION_CODES.O))
        assertTrue(glassBlurSupported(Build.VERSION_CODES.S))
        assertTrue(glassBlurSupported(Build.VERSION_CODES.UPSIDE_DOWN_CAKE))
    }

    @Test
    fun clickableCardIsOneButtonNodeWithMergedLabelAndClicks() {
        var clicks = 0
        composeRule.setContent {
            StayFocusedTheme {
                V5Card(Modifier.testTag("card"), onClick = { clicks++ }) {
                    Text("Active blocks")
                    Text("2 of 3 on")
                }
            }
        }

        composeRule.onNodeWithTag("card").assert(hasRole(Role.Button)).performClick()
        assertEquals(1, clicks)
        // Merged into one node: the children are not separate nodes in the merged tree.
        composeRule.onAllNodes(hasText("Active blocks"), useUnmergedTree = false).assertCountEquals(1)
        composeRule
            .onNodeWithTag("card")
            .assert(hasText("Active blocks") and hasText("2 of 3 on"))
    }

    @Test
    fun nonClickableCardKeepsItsChildrenSeparate() {
        composeRule.setContent {
            StayFocusedTheme {
                V5Card {
                    Text("One")
                    Text("Two")
                }
            }
        }
        composeRule.onNodeWithText("One").assertIsDisplayed()
        composeRule.onNodeWithText("Two").assertIsDisplayed()
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun contentDescriptionOverridesTheMergedLabel() {
        composeRule.setContent {
            StayFocusedTheme {
                GlowCard(
                    V5Gradients.violet,
                    Modifier.testTag("glow"),
                    onClick = {},
                    contentDescription = "Open insights",
                ) {
                    Text("Blocked")
                }
            }
        }
        composeRule
            .onNodeWithTag("glow")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Open insights")))
    }

    @Test
    fun glassTileClicksOnBothBlurPaths() {
        var clicks = 0
        composeRule.setContent {
            StayFocusedTheme {
                Column {
                    GlassTile(Modifier.testTag("blur"), blur = true, onClick = { clicks++ }) { Text("Blocked") }
                    GlassTile(Modifier.testTag("flat"), blur = false, onClick = { clicks++ }) { Text("Focus") }
                }
            }
        }
        composeRule.onNodeWithTag("blur").performClick()
        composeRule.onNodeWithTag("flat").performClick()
        assertEquals(2, clicks)
    }

    @Test
    fun actionTileIs204dpTallSingleButtonWithTitleAndSlot() {
        var clicks = 0
        composeRule.setContent {
            StayFocusedTheme {
                ActionTile("Block\napps", onClick = { clicks++ }, modifier = Modifier.testTag("tile").width(170.dp)) {
                    Text("Active blocks")
                }
            }
        }
        composeRule
            .onNodeWithTag("tile")
            .assertHeightIsEqualTo(204.dp)
            .assertWidthIsEqualTo(170.dp)
            .assert(hasRole(Role.Button) and hasText("Block\napps") and hasText("Active blocks"))
            .performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun gradientActionTileKeepsTheSameSize() {
        composeRule.setContent {
            StayFocusedTheme {
                ActionTile(
                    "Take a\nbreak",
                    onClick = {},
                    modifier = Modifier.testTag("tile").width(170.dp),
                    gradient = V5Gradients.breakTile,
                )
            }
        }
        composeRule.onNodeWithTag("tile").assertHeightIsEqualTo(204.dp)
    }
}
