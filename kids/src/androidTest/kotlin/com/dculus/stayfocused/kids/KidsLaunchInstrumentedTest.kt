package com.dculus.stayfocused.kids

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
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
    val composeRule = createEmptyComposeRule()

    @get:Rule(order = 2)
    val screenshotOnFailure = ScreenshotOnFailureRule()

    @Test
    fun kidsAppLaunches() {
        hiltRule.inject()
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.onNodeWithText("Stay Focused Kids").assertExists()
        }
    }
}
