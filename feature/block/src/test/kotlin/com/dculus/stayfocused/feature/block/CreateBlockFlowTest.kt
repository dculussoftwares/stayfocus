package com.dculus.stayfocused.feature.block

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.SavedStateHandle
import com.dculus.stayfocused.core.model.AppInfo
import com.dculus.stayfocused.core.model.BlockDraft
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.testing.FakeBlockRepository
import com.dculus.stayfocused.core.testing.FakeLinkedDevicesRepository
import com.dculus.stayfocused.core.testing.FakeLockedAppsRepository
import com.dculus.stayfocused.core.testing.FakeSettingsRepository
import com.dculus.stayfocused.core.testing.TestClock
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.usage.InstalledAppsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Creates each of the four block types through the real wizard and ViewModels, then finds the new block in the
 * Block tab's list.
 */
@RunWith(RobolectricTestRunner::class)
@Suppress("LongMethod")
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class CreateBlockFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val blocks = FakeBlockRepository()

    private fun launch() {
        val settings = FakeSettingsRepository()
        val wizard =
            WizardViewModel(
                SavedStateHandle(),
                FakeTargetApps(defaultApps()),
                FakeLinkedDevicesRepository(),
                settings,
                blocks,
                noDeviceBlocks(),
                TestClock(),
                AiDraftHandoff(),
                FakeAiReporter(),
            )
        val installed =
            object : InstalledAppsRepository {
                override fun observeLaunchableApps(): Flow<List<AppInfo>> =
                    flowOf(listOf(AppInfo(pkg("instagram"), "Instagram"), AppInfo(pkg("youtube"), "YouTube")))
            }
        val blockVm =
            BlockViewModel(
                blocks,
                FakeLinkedDevicesRepository(),
                installed,
                settings,
                FakeLockedAppsRepository(),
                FakeUsageRepository(),
                noDeviceBlocks(),
            )
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                StayFocusedTheme {
                    var saved by remember { mutableStateOf(false) }
                    LaunchedEffect(wizard) {
                        wizard.events.collect { if (it is WizardEvent.Saved) saved = true }
                    }
                    if (saved) {
                        val state by blockVm.state.collectAsState()
                        BlockScreen(state, BlockActions())
                    } else {
                        val state by wizard.state.collectAsState()
                        WizardScreen(
                            state,
                            WizardActions(
                                onPickType = wizard::pickType,
                                onNext = wizard::next,
                                onPeriod = wizard::setPeriod,
                                onMins = wizard::setMins,
                                onNow = wizard::setNow,
                                onCycleValue = wizard::setCycleValue,
                                onCycleEdit = wizard::selectCycleEdit,
                                onRange = wizard::setRange,
                                onPreset = wizard::pickSchedulePreset,
                                onDays = wizard::setDays,
                            ),
                        )
                    }
                }
            }
        }
    }

    /** Sets the step-3 ruler the way TalkBack does (a `SetProgress` action), instead of dragging. */
    private fun setRuler(minutes: Int) {
        composeRule.onNodeWithTag(WIZARD_RULER_TAG).performSemanticsAction(SemanticsActions.SetProgress) {
            it(minutes.toFloat())
        }
    }

    private fun createWith(
        type: BlockType,
        expectedName: String,
        onRules: () -> Unit = {},
    ) {
        launch()
        composeRule.onNodeWithTag(wizardTypeTag(type)).performClick()
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("Continue with 2 apps").performClick()
        composeRule.onNodeWithTag(WIZARD_RULES_TAG).assertExists()
        onRules()
        composeRule.onNodeWithText("Turn on block").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(expectedName).assertExists()
        composeRule.onNodeWithText("Instagram + YouTube").assertExists()
    }

    @Test
    fun limitBlockAppearsInTheList() =
        createWith(BlockType.LIMIT, "Daily limit") {
            composeRule.onNodeWithText("Per hour").performClick()
            setRuler(15)
            composeRule
                .onNodeWithText(
                    "Instagram and YouTube can be used 15 min an hour, Mon–Fri. " +
                        "After that they lock until the next hour.",
                ).assertExists()
        }

    @Test
    fun cycleBlockAppearsInTheList() =
        createWith(BlockType.CYCLE, "Use, then lock") {
            composeRule.onNodeWithText("Then lock 30 min").performClick()
            setRuler(60)
            composeRule
                .onNodeWithText(
                    "Open Instagram and YouTube for 10 min, then they lock for 1h. This repeats Mon–Fri.",
                ).assertExists()
        }

    @Test
    fun scheduleBlockAppearsInTheList() =
        createWith(BlockType.SCHEDULE, "Blocked during hours") {
            composeRule.onNodeWithTag(wizardPresetTag(SchedulePreset.Night)).performClick()
            composeRule.onNodeWithTag(wizardPresetTag(SchedulePreset.Night)).assertIsSelected()
            composeRule.onNodeWithText("Instagram and YouTube are blocked from 22:00 to 07:00, Mon–Fri.").assertExists()
        }

    @Test
    fun nowBlockAppearsInTheList() =
        createWith(BlockType.NOW, "Blocked now") {
            setRuler(120)
            composeRule.onNodeWithText("Instagram and YouTube are blocked for the next 2h.").assertExists()
        }

    @Test
    fun customRangeOpensTheTimePicker() {
        launch()
        composeRule.onNodeWithTag(wizardTypeTag(BlockType.SCHEDULE)).performClick()
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("Continue with 2 apps").performClick()
        composeRule.onNodeWithTag(WIZARD_RANGE_CUSTOM_TAG).performClick()
        composeRule.onNodeWithTag(TIME_PICKER_TAG).assertExists()
    }

    @Test
    fun aiDraftShowsTheBannerAndTheCheckTitle() {
        val vm =
            WizardViewModel(
                SavedStateHandle(),
                FakeTargetApps(defaultApps()),
                FakeLinkedDevicesRepository(),
                FakeSettingsRepository(),
                blocks,
                noDeviceBlocks(),
                TestClock(),
                AiDraftHandoff(),
                FakeAiReporter(),
            )
        vm.openAiDraft(BlockDraft(aiNote = "Instagram for 30 minutes a day."))
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                StayFocusedTheme {
                    val state by vm.state.collectAsState()
                    WizardScreen(state, WizardActions(onChangeType = vm::changeType))
                }
            }
        }
        composeRule.onNodeWithText("Set the rule").assertExists()
        composeRule.onNodeWithText("AI draft").assertExists()
        composeRule.onNodeWithText("Instagram for 30 minutes a day.").assertExists()
        composeRule.onNodeWithText("Change").performClick()
        composeRule.onNodeWithText("Rather describe it in words").assertDoesNotExist()
        composeRule.onNodeWithTag(wizardTypeTag(BlockType.LIMIT)).assertExists()
    }
}
