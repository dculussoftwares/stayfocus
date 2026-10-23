package com.dculus.stayfocused.core.ui.charts

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V5ChartsBehaviourTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hourLabelsFollowTheLocale() {
        assertEquals("6 AM", hourLabel(6, Locale.US, is24Hour = false).replace('\u202F', ' '))
        assertEquals("12 PM", hourLabel(12, Locale.US, is24Hour = false).replace('\u202F', ' '))
        assertEquals("11 PM", hourLabel(23, Locale.US, is24Hour = false).replace('\u202F', ' '))
        assertEquals("06", hourLabel(6, Locale.US, is24Hour = true))
        assertTrue(hourLabel(23, Locale.GERMANY, is24Hour = true).startsWith("23"))
    }

    @Test
    fun chartsExposeTheCallersSummary() {
        composeRule.setContent {
            StayFocusedTheme {
                DotGrid(ChartSamples.meHourly, 20, androidx.compose.ui.graphics.Color.White, "dots summary")
                TickProgress(0.5f, "ticks summary")
                HourTicks(ChartSamples.kidHourly, "hours summary")
                GlassBarChart(
                    values = ChartSamples.week,
                    max = 240,
                    average = 176f,
                    averageLabel = "2h 56m",
                    axisLabels = ChartSamples.weekAxis,
                    contentDescription = "week summary",
                    mode = GlassBarMode.Week,
                )
            }
        }
        composeRule.onNodeWithContentDescription("dots summary").assertContentDescriptionEquals("dots summary")
        composeRule.onNodeWithContentDescription("ticks summary").assertExists()
        composeRule.onNodeWithContentDescription("hours summary").assertExists()
        composeRule.onNodeWithContentDescription("week summary").assertExists()
    }

    @Test
    fun emptyAndOutOfRangeDataRenderWithoutCrashing() {
        composeRule.setContent {
            StayFocusedTheme {
                DotGrid(emptyList(), 20, androidx.compose.ui.graphics.Color.White, "empty grid")
                DotGrid(listOf(5, 500, -1), 0, androidx.compose.ui.graphics.Color.White, "zero max")
                TickProgress(Float.NaN, "nan")
                TickProgress(9f, "over")
                HourTicks(emptyList(), "no hours")
                GlassBarChart(emptyList(), 0, 0f, "0", emptyList(), "empty chart")
                GlassBarChart(listOf(0, 0, 0), 20, 0f, "0m/h", listOf("a"), "zeros")
                GlassBarChart(listOf(900, -3), 20, 700f, "x", listOf("a"), "big", highlightIndex = 5)
                UsageRow(icon = {}, name = "App", timeText = "1 min", fraction = 7f)
            }
        }
        composeRule.onNodeWithContentDescription("zeros").assertExists()
        composeRule.onNodeWithContentDescription("big").assertExists()
    }

    @Test
    fun usageRowReadsTheNameOnceBeforeTheTime() {
        composeRule.setContent {
            StayFocusedTheme {
                UsageRow(
                    icon = { Text("I", Modifier.semantics { contentDescription = "Instagram" }) },
                    name = "Instagram",
                    timeText = "52 min",
                    fraction = 0.4f,
                )
            }
        }
        val texts =
            composeRule
                .onAllNodes(isRoot())
                .onFirst()
                .fetchSemanticsNode()
                .let { root -> collectLabels(root) }
        assertEquals(listOf("Instagram", "52 min"), texts)
    }

    private fun collectLabels(node: SemanticsNode): List<String> {
        val own =
            node.config
                .getOrNull(SemanticsProperties.Text)
                .orEmpty()
                .map { it.text } +
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        return own + node.children.flatMap { collectLabels(it) }
    }

    @Test
    fun usageRowReadsAsNameAndTime() {
        composeRule.setContent {
            StayFocusedTheme {
                UsageRow(
                    icon = { Text("I", Modifier.testTag("icon")) },
                    name = "Instagram",
                    timeText = "52 min",
                    fraction = 0.4f,
                )
            }
        }
        composeRule.onNodeWithTag("icon", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Instagram", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("52 min", useUnmergedTree = true).assertExists()
    }
}
