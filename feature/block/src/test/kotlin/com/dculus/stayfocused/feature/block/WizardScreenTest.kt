package com.dculus.stayfocused.feature.block

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.testing.FakeLinkedDevicesRepository
import com.dculus.stayfocused.core.testing.FakeSettingsRepository
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The wizard host driven by its real ViewModel: step 1 to step 2 to step 3. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class WizardScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun launch() {
        val settings = FakeSettingsRepository()
        kotlinx.coroutines.runBlocking { settings.setAiEnabled(true) }
        val vm =
            WizardViewModel(SavedStateHandle(), FakeTargetApps(defaultApps()), FakeLinkedDevicesRepository(), settings)
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                StayFocusedTheme {
                    val state by vm.state.collectAsState()
                    WizardScreen(
                        state,
                        WizardActions(
                            onBack = vm::back,
                            onPickType = vm::pickType,
                            onToggleApp = vm::toggleApp,
                            onNext = vm::next,
                            onDescribe = vm::describeWithAi,
                            onSelectTarget = vm::selectTarget,
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun walksFromTheTypeToTheAppsToTheRules() {
        launch()
        composeRule.onNodeWithText("Continue").assertExists()
        composeRule.onNodeWithTag(wizardTypeTag(BlockType.LIMIT)).assertIsSelected()
        composeRule.onNodeWithText("Rather just describe it?").assertExists()

        composeRule.onNodeWithTag(wizardTypeTag(BlockType.SCHEDULE)).performClick()
        composeRule.onNodeWithText("Which apps?").assertExists()
        composeRule.onNodeWithText("2 SELECTED").assertExists()
        composeRule.onNodeWithText("Continue with 2 apps").assertExists()

        composeRule.onNodeWithTag(wizardAppTag(pkg("reddit"))).performClick()
        composeRule.onNodeWithTag(wizardAppTag(pkg("reddit"))).assertIsOn()
        composeRule.onNodeWithText("Continue with 3 apps").performClick()

        composeRule.onNodeWithTag(WIZARD_RULES_TAG).assertExists()
        composeRule.onNodeWithText("When should it lock?").assertExists()
        composeRule.onNodeWithText("Turn on block").assertExists()
    }

    @Test
    fun singularCtaForOneApp() {
        launch()
        composeRule.onNodeWithTag(wizardTypeTag(BlockType.NOW)).performClick()
        composeRule.onNodeWithTag(wizardAppTag(pkg("instagram"))).performClick()
        composeRule.onNodeWithText("Continue with 1 app").assertExists()
    }

    @Test
    fun backReturnsToTheTypeStep() {
        launch()
        composeRule.onNodeWithTag(wizardTypeTag(BlockType.CYCLE)).performClick()
        composeRule.onNodeWithTag(WIZARD_BACK_TAG).performClick()
        composeRule.onNodeWithText("Rather just describe it?").assertExists()
        composeRule.onNodeWithTag(wizardTypeTag(BlockType.CYCLE)).assertIsSelected()
    }
}
