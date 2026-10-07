package com.dculus.stayfocused.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.model.DialConfig
import com.dculus.stayfocused.core.model.DialConfigs
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.github.takahirom.roborazzi.captureRoboImage
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
class RotaryDialTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var value by mutableIntStateOf(60)

    private val dialNode
        get() = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))

    private fun setDial(
        initial: Int,
        config: DialConfig,
    ) {
        value = initial
        composeRule.setContent {
            StayFocusedTheme {
                Box(Modifier.background(StayFocusedTheme.colors.background).padding(18.dp)) {
                    RotaryDial(value = value, onValueChange = { value = it }, config = config)
                }
            }
        }
    }

    private fun shot(
        name: String,
        initial: Int,
        config: DialConfig,
    ) {
        setDial(initial, config)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/components/RotaryDial$name.png")
    }

    @Test fun dial5() = shot("5", 5, DialConfigs.Break)

    @Test fun dial30() = shot("30", 30, DialConfigs.Break)

    @Test fun dial90() = shot("90", 90, DialConfigs.Break)

    @Test fun dial480() = shot("480", 480, DialConfigs.BlockNow)

    @Test
    fun draggingToThreeOClockOnBreakDialGivesThirty() {
        setDial(5, DialConfigs.Break)
        dialNode.performTouchInput {
            val c = center
            down(Offset(c.x, c.y - 100.dp.toPx()))
            moveTo(Offset(c.x + 100.dp.toPx(), c.y))
            up()
        }
        assertEquals(30, value)
    }

    @Test
    fun tapOnTopOfRingSetsMax() {
        setDial(30, DialConfigs.Break)
        dialNode.performTouchInput { click(Offset(center.x, center.y - 100.dp.toPx())) }
        assertEquals(120, value)
    }

    @Test
    fun setProgressSnapsToStepAndClamps() {
        setDial(30, DialConfigs.Break)
        dialNode.performSemanticsAction(SemanticsActions.SetProgress) { it(47f) }
        assertEquals(45, value)
        dialNode.performSemanticsAction(SemanticsActions.SetProgress) { it(500f) }
        assertEquals(120, value)
        dialNode.performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        assertEquals(5, value)
    }

    @Test
    fun centerShowsLabelAndUnit() {
        setDial(90, DialConfigs.Break)
        composeRule.onNodeWithText("1:30", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("HOURS", useUnmergedTree = true).assertExists()
    }

    @Test
    fun presetsPick() {
        var picked = -1
        composeRule.setContent {
            StayFocusedTheme { DialPresets(DialConfigs.Break, value = 30, onPick = { picked = it }) }
        }
        composeRule.onNodeWithText("1h").performClick()
        assertEquals(60, picked)
    }
}
