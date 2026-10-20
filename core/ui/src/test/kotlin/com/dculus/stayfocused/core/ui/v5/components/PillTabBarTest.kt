package com.dculus.stayfocused.core.ui.v5.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme
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
@Config(sdk = [34], qualifiers = "w390dp-h160dp-xhdpi")
class PillTabBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val tabs = listOf(PillTab("home", "Home"), PillTab("block", "Blocks"), PillTab("devices", "Family"))

    @Composable
    private fun BarUnderTest(
        selectedId: String?,
        onSelect: (String) -> Unit,
    ) = V5Theme { PillTabBar(tabs, selectedId, onSelect) }

    @Test
    fun selectionClickAndTabRole() {
        var selected by mutableStateOf<String?>("home")
        composeRule.setContent { BarUnderTest(selected) { selected = it } }
        composeRule.onNodeWithTag(v5TabTag("home")).assertIsSelected()
        composeRule.onNodeWithTag(v5TabTag("block")).assertIsNotSelected()
        composeRule.onNodeWithTag(v5TabTag("block")).assert(
            SemanticsMatcher("Role.Tab") { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab },
        )
        composeRule.onNodeWithTag(v5TabTag("block")).performClick()
        assertEquals("block", selected)
        composeRule.onNodeWithTag(v5TabTag("block")).assertIsSelected()
    }

    @Test
    fun barWithBackdropStillComposesAndSelects() {
        var selected by mutableStateOf<String?>("home")
        composeRule.setContent {
            V5Theme {
                val backdrop = rememberPillTabBarBackdrop()
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().recordBackdrop(backdrop)) {
                    PillTabBar(tabs, selected, { selected = it }, Modifier.align(Alignment.BottomCenter), backdrop)
                }
            }
        }
        composeRule.onNodeWithTag(v5TabTag("devices")).performClick()
        composeRule.onNodeWithTag(v5TabTag("devices")).assertIsSelected()
    }

    @Test
    fun noSelectionMatchesNoTab() {
        composeRule.setContent { BarUnderTest(null) {} }
        tabs.forEach { composeRule.onNodeWithTag(v5TabTag(it.id)).assertIsNotSelected() }
    }

    @Test
    fun contentPaddingAddsBarMarginGapAndInset() {
        assertEquals(60.dp, PillTabBarHeight)
        assertEquals(98.dp, pillTabBarContentPadding(0.dp))
        assertEquals(122.dp, pillTabBarContentPadding(24.dp))
    }

    @Test
    fun screenshot() {
        composeRule.setContent { CompositionLocalProvider(LocalInspectionMode provides true) { PillTabBarPreview() } }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/v5/components/PillTabBar.png")
    }
}
