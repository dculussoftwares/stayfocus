package com.dculus.stayfocused.core.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric SDK 36 needs JDK 21 and CI runs Gradle on 17, so API 36 is a manual check.
// See docs/qa/pending-manual-checks.md.

/** Runs on API 26 (decor-view flags) and API 34 (WindowInsetsController). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class V5ScreenSystemBarsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun lightIcons(): Pair<Boolean, Boolean> {
        val window = composeRule.activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        // "Appearance light" = dark icons.
        return !controller.isAppearanceLightStatusBars to !controller.isAppearanceLightNavigationBars
    }

    @Test
    fun darkContentGivesLightIcons() {
        composeRule.setContent { V5Screen(darkContent = true) {} }
        composeRule.runOnIdle { assertEquals(true to true, lightIcons()) }
    }

    @Test
    fun lightContentGivesDarkIcons() {
        composeRule.setContent { V5Screen(darkContent = false) {} }
        composeRule.runOnIdle { assertEquals(false to false, lightIcons()) }
    }

    @Test
    fun iconsFollowDarkContentChanges() {
        var dark by mutableStateOf(true)
        composeRule.setContent { V5Screen(darkContent = dark) {} }
        composeRule.runOnIdle { assertEquals(true to true, lightIcons()) }
        dark = false
        composeRule.runOnIdle { assertEquals(false to false, lightIcons()) }
    }

    @Test
    fun removingTheTopScreenReappliesTheOneBelow() {
        var showTop by mutableStateOf(true)
        composeRule.setContent {
            V5Screen(darkContent = false) {}
            if (showTop) V5Screen(darkContent = true) {}
        }
        composeRule.runOnIdle { assertEquals(true to true, lightIcons()) }
        showTop = false
        composeRule.runOnIdle { assertEquals(false to false, lightIcons()) }
    }

    @Test
    fun changingTheScreenBelowKeepsTheTopScreensIcons() {
        var belowDark by mutableStateOf(false)
        composeRule.setContent {
            V5Screen(darkContent = belowDark) {}
            V5Screen(darkContent = true) {}
        }
        composeRule.runOnIdle { assertEquals(true to true, lightIcons()) }
        belowDark = true
        composeRule.runOnIdle { assertEquals(true to true, lightIcons()) }
        belowDark = false
        composeRule.runOnIdle { assertEquals(true to true, lightIcons()) }
    }

    @Test
    fun leavingTheLastV5ScreenRestoresTheWindow() {
        val before = composeRule.runOnIdle { lightIcons() }
        var show by mutableStateOf(true)
        composeRule.setContent { if (show) V5Screen(darkContent = !before.first) {} }
        composeRule.runOnIdle { assertEquals(!before.first to !before.second, lightIcons()) }
        show = false
        composeRule.runOnIdle { assertEquals(before, lightIcons()) }
    }
}
