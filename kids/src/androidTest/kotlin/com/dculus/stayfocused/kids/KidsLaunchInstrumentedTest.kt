package com.dculus.stayfocused.kids

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.dculus.stayfocused.core.testing.ScenarioCleanupRule
import com.dculus.stayfocused.core.testing.ScreenshotOnFailureRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

/** The Kids app starts and shows its placeholder screen. */
@HiltAndroidTest
class KidsLaunchInstrumentedTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val scenarios = ScenarioCleanupRule()

    @get:Rule(order = 2)
    val composeRule = createEmptyComposeRule()

    @get:Rule(order = 3)
    val screenshotOnFailure = ScreenshotOnFailureRule()

    @Test
    fun kidsAppLaunches() {
        hiltRule.inject()
        scenarios.launch(MainActivity::class.java)
        composeRule.onNodeWithText("Stay Focused Kids").assertExists()
    }
}
