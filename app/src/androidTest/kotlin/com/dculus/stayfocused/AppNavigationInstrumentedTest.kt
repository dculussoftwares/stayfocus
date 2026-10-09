package com.dculus.stayfocused

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.dculus.stayfocused.core.data.repository.SettingsRepository
import com.dculus.stayfocused.core.testing.ScenarioCleanupRule
import com.dculus.stayfocused.core.testing.ScreenshotOnFailureRule
import com.dculus.stayfocused.core.ui.components.SF_TAB_BAR_TAG
import com.dculus.stayfocused.core.ui.components.sfTabTag
import com.dculus.stayfocused.navigation.MainTab
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/** The app on a real Android system: launch, onboarding to Home, the 4-tab bar and back behaviour (M1-09). */
@HiltAndroidTest
class AppNavigationInstrumentedTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    // Closes the activity after the failure screenshot is taken (see ScenarioCleanupRule).
    @get:Rule(order = 1)
    val scenarios = ScenarioCleanupRule()

    @get:Rule(order = 2)
    val composeRule = createEmptyComposeRule()

    @get:Rule(order = 3)
    val screenshotOnFailure = ScreenshotOnFailureRule()

    @Inject
    lateinit var settings: SettingsRepository

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun inject() = hiltRule.inject()

    private fun launch(onboardingComplete: Boolean) {
        // The DataStore file outlives a test, so seed the flag before the activity reads it.
        runBlocking { settings.setOnboardingComplete(onboardingComplete) }
        scenario = scenarios.launch(MainActivity::class.java)
    }

    private fun tab(tab: MainTab) = composeRule.onNodeWithTag(sfTabTag(tab.name))

    private fun tabBarVisible() = composeRule.onAllNodesWithTag(SF_TAB_BAR_TAG).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun appLaunchesOnHomeWithFourTabs() {
        launch(onboardingComplete = true)
        composeRule.waitUntil(TIMEOUT_MS) { tabBarVisible() }
        MainTab.entries.forEach { tab(it).assertExists() }
        tab(MainTab.HOME).assertIsSelected()
    }

    @Test
    fun onboardingRunsToHome() {
        launch(onboardingComplete = false)
        composeRule.waitUntil(
            TIMEOUT_MS,
        ) { composeRule.onAllNodesWithText("Get started").fetchSemanticsNodes().isNotEmpty() }
        assertTrue("no tab bar during onboarding", !tabBarVisible())
        composeRule.onNodeWithText("Get started").performClick()
        listOf("Continue without an account", "Continue").forEach { label ->
            composeRule.waitUntil(
                TIMEOUT_MS,
            ) { composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
            composeRule.onNodeWithText(label).performClick()
        }
        composeRule.waitUntil(TIMEOUT_MS) { tabBarVisible() }
        tab(MainTab.HOME).assertIsSelected()
    }

    @Test
    fun tabsSwitchAndBackFromANonHomeTabGoesHome() {
        launch(onboardingComplete = true)
        composeRule.waitUntil(TIMEOUT_MS) { tabBarVisible() }
        tab(MainTab.BLOCK).performClick()
        tab(MainTab.BLOCK).assertIsSelected()
        tab(MainTab.HOME).assertIsNotSelected()
        tab(MainTab.INSIGHTS).performClick()
        tab(MainTab.INSIGHTS).assertIsSelected()
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        tab(MainTab.HOME).assertIsSelected()
        assertEquals("back from a tab must not exit", Lifecycle.State.RESUMED, scenario?.state)
    }

    @Test
    fun backFromHomeExits() {
        launch(onboardingComplete = true)
        composeRule.waitUntil(TIMEOUT_MS) { tabBarVisible() }
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitUntil(TIMEOUT_MS) { scenario?.state == Lifecycle.State.DESTROYED }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
