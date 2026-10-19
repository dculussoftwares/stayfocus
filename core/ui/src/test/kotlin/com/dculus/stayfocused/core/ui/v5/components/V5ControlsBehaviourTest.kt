package com.dculus.stayfocused.core.ui.v5.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.ui.v5.theme.V5Colors
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V5ControlsBehaviourTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private fun hasStateDescription(text: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    private fun isSelected(selected: Boolean) = SemanticsMatcher.expectValue(SemanticsProperties.Selected, selected)

    private fun SemanticsNodeInteraction.assertTouchTarget48() =
        assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)

    // ---- DayChips ----

    @Test
    fun toggleDayAddsAndRemoves() {
        val start = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
        assertEquals(start + DayOfWeek.SUNDAY, toggleDay(start, DayOfWeek.SUNDAY))
        assertEquals(setOf(DayOfWeek.FRIDAY), toggleDay(start, DayOfWeek.MONDAY))
    }

    @Test
    fun toggleDayRefusesToTurnOffTheLastDay() {
        val only = setOf(DayOfWeek.WEDNESDAY)
        assertSame(only, toggleDay(only, DayOfWeek.WEDNESDAY))
    }

    @Test
    fun dayChipsKeepOneDayOnAndAre48dpTargets() {
        var selected by mutableStateOf(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY))
        composeRule.setContent { V5Theme { DayChips(selected, { selected = it }) } }

        composeRule.onNodeWithContentDescription("Monday").assertTouchTarget48().performClick()
        assertEquals(setOf(DayOfWeek.TUESDAY), selected)
        composeRule.onNodeWithContentDescription("Tuesday").performClick()
        assertEquals(setOf(DayOfWeek.TUESDAY), selected)
        composeRule.onNodeWithContentDescription("Tuesday").assertIsOn()
        composeRule.onNodeWithContentDescription("Monday").assertIsOff()
    }

    // ---- V5Toggle ----

    @Test
    fun toggleExposesSwitchRoleAndStateAndFlips() {
        var checked by mutableStateOf(false)
        composeRule.setContent { V5Theme { V5Toggle(checked, { checked = it }) } }

        val node = composeRule.onNode(hasRole(Role.Switch))
        node.assertTouchTarget48()
        node.assert(hasStateDescription("Off")).assertIsOff()
        node.performClick()
        node.assert(hasStateDescription("On")).assertIsOn()
        assertTrue(checked)
    }

    @Test
    fun disabledToggleIgnoresTaps() {
        var checked by mutableStateOf(false)
        composeRule.setContent { V5Theme { V5Toggle(checked, { checked = it }, enabled = false) } }

        composeRule.onNode(hasRole(Role.Switch)).assertIsNotEnabled().performClick()
        assertFalse(checked)
    }

    // ---- SegmentedPill ----

    @Test
    fun segmentedPillExposesTabsWithSelection() {
        var selected by mutableIntStateOf(0)
        composeRule.setContent {
            V5Theme {
                SegmentedPill(
                    listOf("Day", "Week", "Month"),
                    selected,
                    { selected = it },
                    size = SegmentedPillSize.Small,
                )
            }
        }
        val day = composeRule.onNode(hasRole(Role.Tab) and hasText("Day"))
        val week = composeRule.onNode(hasRole(Role.Tab) and hasText("Week"))

        day.assert(isSelected(true))
        week.assert(isSelected(false)).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, selected)
        week.assert(isSelected(true))
        day.assert(isSelected(false))
    }

    // ---- PillButton ----

    @Test
    fun pillButtonClicksAndMeets48dpEvenWhenSmall() {
        var clicks = 0
        composeRule.setContent {
            V5Theme {
                PillButton("Dismiss", { clicks++ }, variant = PillButtonVariant.Secondary, size = PillButtonSize.Small)
            }
        }
        composeRule
            .onNodeWithText("Dismiss")
            .assertHeightIsAtLeast(48.dp)
            .assertIsEnabled()
            .performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun loadingPillButtonAnnouncesLoadingShowsLoadingLabelAndIgnoresTaps() {
        var clicks = 0
        composeRule.setContent {
            V5Theme { PillButton("Continue", { clicks++ }, loading = true, loadingLabel = "Signing in") }
        }
        val node = composeRule.onNodeWithText("Signing in")
        node.assert(hasStateDescription("Loading")).assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun disabledPillButtonIgnoresTaps() {
        var clicks = 0
        composeRule.setContent { V5Theme { PillButton("Continue", { clicks++ }, enabled = false) } }

        composeRule.onNodeWithText("Continue").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }

    // ---- RoundIconButton / tags ----

    @Test
    fun roundIconButtonAnnouncesLabelAndBadgeState() {
        var clicks = 0
        composeRule.setContent {
            V5Theme {
                RoundIconButton(
                    ControlIcons.bell,
                    "Alerts",
                    { clicks++ },
                    showBadge = true,
                    badgeDescription = "New alert",
                )
            }
        }
        composeRule
            .onNode(hasContentDescription("Alerts") and hasRole(Role.Button))
            .assert(hasStateDescription("New alert"))
            .assertTouchTarget48()
            .performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun clickableTargetTagIsA48dpButton() {
        var clicks = 0
        composeRule.setContent { V5Theme { Column { TargetTag("Aarav's phone", onClick = { clicks++ }) } } }

        composeRule.onNode(hasRole(Role.Button)).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, clicks)
    }

    // ---- V5TextField ----

    @Test
    fun passwordFieldTakesInputShowsErrorAndCanBeRevealed() {
        var text by mutableStateOf("")
        composeRule.setContent {
            V5Theme {
                V5TextField(text, { text = it }, "Password (6+ characters)", isPassword = true, error = "Too short.")
            }
        }
        composeRule.onNodeWithContentDescription("Password (6+ characters)").performTextInput("secret")
        assertEquals("secret", text)
        composeRule.onNodeWithText("Too short.").assertIsEnabled()
        composeRule.onNodeWithContentDescription("Show password").assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithContentDescription("Hide password").assertIsEnabled()
    }

    // ---- AppIconTile colours ----

    @Test
    fun knownPackagesUseBrandColours() {
        val colors = V5Colors()
        assertEquals(0xFFE1306C.toInt(), appTileColors("com.instagram.android", colors).background.toArgb())
        assertEquals(colors.ink, appTileColors("com.snapchat.android", colors).letter)
    }

    @Test
    fun unknownPackagesGetAStableAccentColour() {
        val colors = V5Colors()
        val first = appTileColors("com.example.unknown", colors)
        assertEquals(first, appTileColors("com.example.unknown", colors))
        assertEquals(colors.onGradient, first.letter)
        val palette = (0..40).map { appTileColors("pkg.number$it", colors).background }.toSet()
        assertTrue("palette should vary, got ${palette.size}", palette.size > 1)
        assertNotEquals(colors.surface, first.background)
    }
}
