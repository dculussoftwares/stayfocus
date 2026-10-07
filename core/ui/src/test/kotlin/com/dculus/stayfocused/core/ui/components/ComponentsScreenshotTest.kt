package com.dculus.stayfocused.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
class ComponentsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun shot(
        name: String,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent { CompositionLocalProvider(LocalInspectionMode provides true) { content() } }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/components/$name.png")
    }

    @Test fun buttons() = shot("Buttons") { ButtonsPreview() }

    @Test fun panels() = shot("Panels") { PanelsPreview() }

    @Test fun toggle() = shot("SfToggle") { SfTogglePreview() }

    @Test fun chips() = shot("Chips") { ChipsPreview() }

    @Test fun indicators() = shot("Indicators") { IndicatorsPreview() }

    @Test fun textFields() = shot("SfTextField") { SfTextFieldPreview() }

    @Test
    fun toggleHasSwitchRoleAndTouchTarget() {
        var checked by mutableStateOf(false)
        composeRule.setContent {
            StayFocusedTheme { SfToggle(checked = checked, onCheckedChange = { checked = it }) }
        }
        val node = composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        node.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        node.performClick()
        assertEquals(true, checked)
    }

    @Test
    fun passwordToggleRevealsAndHides() {
        composeRule.setContent {
            StayFocusedTheme { SfTextField("secret", {}, "Password", isPassword = true) }
        }
        composeRule.onNodeWithContentDescription("Show password").assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithContentDescription("Hide password").assertExists()
        composeRule.onNodeWithText("HIDE").assertExists()
    }
}
