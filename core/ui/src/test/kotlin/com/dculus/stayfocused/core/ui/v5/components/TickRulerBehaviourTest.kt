package com.dculus.stayfocused.core.ui.v5.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.model.RulerScale
import com.dculus.stayfocused.core.model.RulerScales
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TickRulerBehaviourTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val tag = "ruler"
    private val emitted = mutableListOf<Int>()
    private var value by mutableIntStateOf(0)

    private fun setRuler(
        scale: RulerScale = RulerScales.Limit,
        start: Int = scale.min,
    ) {
        value = start
        emitted.clear()
        composeRule.setContent {
            V5Theme {
                Box(Modifier.width(300.dp)) {
                    TickRuler(
                        value = value,
                        onValueChange = {
                            emitted += it
                            value = it
                        },
                        scale = scale,
                        colors = TickRulerColors.onGradient(),
                        contentDescription = "Minutes",
                        valueDescription = "$value minutes",
                        modifier = Modifier.testTag(tag),
                    )
                }
            }
        }
    }

    @Test
    fun isSeventyTwoDpTall() {
        setRuler()
        composeRule.onNodeWithTag(tag).assertHeightIsEqualTo(72.dp)
    }

    @Test
    fun draggingFromLeftToRightEdgeSweepsMinToMax() {
        setRuler(start = 120)
        composeRule.onNodeWithTag(tag).performTouchInput {
            swipe(Offset(0f, centerY), Offset(width - 1f, centerY), durationMillis = 400)
        }
        composeRule.waitForIdle()
        assertEquals(RulerScales.Limit.min, emitted.first())
        assertEquals(RulerScales.Limit.max, emitted.last())
        assertEquals(RulerScales.Limit.max, value)
        assertEquals("sweep is non-decreasing", emitted.sorted(), emitted)
    }

    @Test
    fun draggingBackFromRightToLeftSweepsMaxToMin() {
        setRuler(start = 120)
        composeRule.onNodeWithTag(tag).performTouchInput {
            swipe(Offset(width - 1f, centerY), Offset(0f, centerY), durationMillis = 400)
        }
        composeRule.waitForIdle()
        assertEquals(RulerScales.Limit.max, emitted.first())
        assertEquals(RulerScales.Limit.min, emitted.last())
    }

    @Test
    fun tapSetsTheNearestStep() {
        setRuler(RulerScales.Use, start = 1)
        composeRule.onNodeWithTag(tag).performTouchInput { click(Offset(width / 2f, centerY)) }
        composeRule.waitForIdle()
        // 1..60 step 1: the centre is 30.5 -> 31
        assertEquals(31, value)
        composeRule.onNodeWithTag(tag).performTouchInput { click(Offset(width * 0.25f, centerY)) }
        composeRule.waitForIdle()
        assertEquals(RulerScales.Use.valueAt(0.25), value)
    }

    @Test
    fun tappingTheValueAlreadySetDoesNotEmit() {
        setRuler(RulerScales.Use, start = 1)
        composeRule.onNodeWithTag(tag).performTouchInput { click(Offset(1f, centerY)) }
        composeRule.waitForIdle()
        assertTrue(emitted.isEmpty())
    }

    @Test
    fun exposesSliderSemanticsWithSteps() {
        setRuler(RulerScales.Break, start = 30)
        val node = composeRule.onNodeWithTag(tag).fetchSemanticsNode()
        val info = node.config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(30f, info.current)
        assertEquals(5f..120f, info.range)
        assertEquals(22, info.steps)
        assertEquals("30 minutes", node.config.getOrNull(SemanticsProperties.StateDescription))
    }

    @Test
    fun setProgressSnapsToTheStepAndClamps() {
        setRuler(RulerScales.Limit, start = 5)
        composeRule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.SetProgress) { it(47f) }
        composeRule.waitForIdle()
        assertEquals(45, value)
        composeRule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.SetProgress) { it(5000f) }
        composeRule.waitForIdle()
        assertEquals(240, value)
    }

    @Test
    fun arrowAndVolumeKeysMoveOneStep() {
        setRuler(RulerScales.Limit, start = 45)
        val node = composeRule.onNodeWithTag(tag)
        node.requestFocus()
        node.performKeyInput { pressKey(Key.DirectionRight) }
        assertEquals(50, value)
        node.performKeyInput { pressKey(Key.VolumeDown) }
        assertEquals(45, value)
        node.performKeyInput { pressKey(Key.DirectionLeft) }
        assertEquals(40, value)
        node.performKeyInput { pressKey(Key.VolumeUp) }
        assertEquals(45, value)
    }

    @Test
    fun keyRepeatsBeforeTheParentRecomposesEachAdvanceOneStep() {
        val seen = mutableListOf<Int>()
        composeRule.setContent {
            V5Theme {
                Box(Modifier.width(300.dp)) {
                    TickRuler(
                        value = 45,
                        onValueChange = { seen += it }, // the parent never feeds the value back
                        scale = RulerScales.Limit,
                        colors = TickRulerColors.onGradient(),
                        contentDescription = "Minutes",
                        valueDescription = "45 minutes",
                        modifier = Modifier.testTag(tag),
                    )
                }
            }
        }
        val node = composeRule.onNodeWithTag(tag)
        node.requestFocus()
        node.performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionRight)
        }
        assertEquals(listOf(50, 55), seen)
    }

    @Test
    fun keyAfterADragBeforeRecompositionStartsFromTheDraggedValue() {
        val seen = mutableListOf<Int>()
        composeRule.setContent {
            V5Theme {
                Box(Modifier.width(300.dp)) {
                    TickRuler(
                        value = 45,
                        onValueChange = { seen += it }, // the parent never feeds the value back
                        scale = RulerScales.Use,
                        colors = TickRulerColors.onGradient(),
                        contentDescription = "Minutes",
                        valueDescription = "45 minutes",
                        modifier = Modifier.testTag(tag),
                    )
                }
            }
        }
        val node = composeRule.onNodeWithTag(tag)
        node.performTouchInput { click(Offset(width - 1f, centerY)) }
        node.requestFocus()
        node.performKeyInput { pressKey(Key.DirectionLeft) }
        assertEquals(listOf(60, 59), seen)
    }

    @Test
    fun setProgressRoundsFractionalTargetsToTheNearestStep() {
        setRuler(RulerScales.Limit, start = 5)
        composeRule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.SetProgress) { it(47.5f) }
        composeRule.waitForIdle()
        assertEquals(50, value)
    }

    @Test
    fun keyStepMapsOnlyAdjustKeysOnKeyDown() {
        assertEquals(1, keyStepOf(KeyEventType.KeyDown, Key.VolumeUp))
        assertEquals(-1, keyStepOf(KeyEventType.KeyDown, Key.VolumeDown))
        assertEquals(1, keyStepOf(KeyEventType.KeyDown, Key.DirectionUp))
        assertEquals(-1, keyStepOf(KeyEventType.KeyDown, Key.DirectionDown))
        assertEquals(0, keyStepOf(KeyEventType.KeyUp, Key.VolumeUp))
        assertEquals(0, keyStepOf(KeyEventType.KeyDown, Key.A))
    }

    @Test
    fun labelsShowMinDragToSetMax() {
        composeRule.setContent {
            V5Theme { RulerLabels(RulerScales.Break.min, RulerScales.Break.max, Modifier.fillMaxWidth()) }
        }
        composeRule.onNodeWithText("5 min").assertExists()
        composeRule.onNodeWithText("Drag to set").assertExists()
        composeRule.onNodeWithText("2h").assertExists()
    }
}
