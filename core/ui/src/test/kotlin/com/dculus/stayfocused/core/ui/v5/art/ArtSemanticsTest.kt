package com.dculus.stayfocused.core.ui.v5.art

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Decorative art is hidden from TalkBack; BreakArt only announces a description it is given. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArtSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val hasNoDescription = SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription)

    @Test
    fun decorativeArtExposesNoText() {
        composeRule.setContent {
            V5Theme {
                Column {
                    BreakArt(lengthMins = 30, modifier = Modifier.testTag("break"))
                    LinkArt(Modifier.testTag("link"))
                    ScanFrame(Modifier.testTag("scan"))
                    Spinner(Modifier.testTag("spinner"))
                    PulseDot(Color.Green, Modifier.testTag("pulse"))
                }
            }
        }
        listOf("break", "link", "scan", "spinner", "pulse").forEach { tag ->
            composeRule.onNodeWithTag(tag).assert(hasNoDescription)
        }
        composeRule.onAllNodesWithText("min").assertCountEquals(0)
        composeRule.onAllNodesWithText("30").assertCountEquals(0)
    }

    @Test
    fun breakArtAnnouncesAGivenDescriptionAsOneNode() {
        composeRule.setContent {
            V5Theme {
                BreakArt(lengthMins = 30, modifier = Modifier.testTag("break"), contentDescription = "30 minute break")
            }
        }
        composeRule.onNodeWithTag("break").assertContentDescriptionEquals("30 minute break")
        composeRule.onAllNodesWithText("min").assertCountEquals(0)
    }
}
