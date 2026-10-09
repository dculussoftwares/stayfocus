package com.dculus.stayfocused.feature.block

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import com.dculus.stayfocused.core.model.AppInfo
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
                TestClock(),
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
                                onToggleDay = wizard::toggleDay,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun createWith(
        type: BlockType,
        expectedName: String,
        onRules: () -> Unit = {},
    ) {
        launch()
        composeRule.onNodeWithTag(wizardTypeTag(type)).performClick()
        composeRule.onNodeWithText("Continue with 2 apps").performClick()
        composeRule.onNodeWithTag(WIZARD_RULES_TAG).assertExists()
        onRules()
        composeRule.onNodeWithText("Turn on block").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(expectedName).assertExists()
    }

    @Test
    fun limitBlockAppearsInTheList() =
        createWith(BlockType.LIMIT, "Time limit") {
            composeRule.onNodeWithText("Per hour").performClick()
            composeRule.onNodeWithText("15m").performClick()
            composeRule.onNodeWithText("Lock Instagram and YouTube after 15 min an hour, on weekdays.").assertExists()
        }

    @Test
    fun cycleBlockAppearsInTheList() =
        createWith(BlockType.CYCLE, "Use, then rest") {
            composeRule.onNodeWithText("THEN LOCKED").performClick()
            composeRule.onNodeWithText("1h").performClick()
            composeRule
                .onNodeWithText(
                    "Each time you open Instagram and YouTube, you get 10 min. Then it locks for 1 h, on weekdays.",
                ).assertExists()
        }

    @Test
    fun scheduleBlockAppearsInTheList() =
        createWith(BlockType.SCHEDULE, "Scheduled block") {
            composeRule.onNodeWithTag(wizardRangeTag("22:00–07:00")).performClick()
            composeRule.onNodeWithTag(wizardRangeTag("22:00–07:00")).assertIsSelected()
            composeRule.onNodeWithText("Lock Instagram and YouTube from 22:00 to 07:00, on weekdays.").assertExists()
        }

    @Test
    fun nowBlockAppearsInTheList() =
        createWith(BlockType.NOW, "Quick block") {
            composeRule.onNodeWithText("2h").performClick()
            composeRule.onNodeWithText("Lock Instagram and YouTube for the next 2 h.").assertExists()
        }

    @Test
    fun customRangeOpensTheTimePicker() {
        launch()
        composeRule.onNodeWithTag(wizardTypeTag(BlockType.SCHEDULE)).performClick()
        composeRule.onNodeWithText("Continue with 2 apps").performClick()
        composeRule.onNodeWithTag(WIZARD_RANGE_CUSTOM_TAG).performClick()
        composeRule.onNodeWithTag(TIME_PICKER_TAG).assertExists()
    }
}
