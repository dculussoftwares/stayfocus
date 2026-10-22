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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.SavedStateHandle
import com.dculus.stayfocused.core.model.AppInfo
import com.dculus.stayfocused.core.model.BlockSource
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.testing.FakeBlockRepository
import com.dculus.stayfocused.core.testing.FakeLinkedDevicesRepository
import com.dculus.stayfocused.core.testing.FakeLockedAppsRepository
import com.dculus.stayfocused.core.testing.FakeSettingsRepository
import com.dculus.stayfocused.core.testing.TestClock
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.usage.InstalledAppsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Sentence, draft, edit, turn on: the describe flow through the real Block tab, wizard and ViewModels. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class AiDescribeFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val blocks = FakeBlockRepository()
    private val handoff = AiDraftHandoff()
    private val reporter = FakeAiReporter()
    private val settings = FakeSettingsRepository()

    @Suppress("LongMethod")
    private fun launch() {
        runBlocking { settings.setAiEnabled(true) }
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
                FakeBlockDescriber(),
                handoff,
                noDeviceBlocks(),
            )
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                StayFocusedTheme {
                    var wizard by remember { mutableStateOf<WizardViewModel?>(null) }
                    LaunchedEffect(blockVm) {
                        blockVm.events.collect { event ->
                            if (event is BlockEvent.OpenWizard) {
                                wizard =
                                    WizardViewModel(
                                        SavedStateHandle(mapOf("prefill" to event.prefill)),
                                        FakeTargetApps(defaultApps()),
                                        FakeLinkedDevicesRepository(),
                                        settings,
                                        blocks,
                                        noDeviceBlocks(),
                                        TestClock(),
                                        handoff,
                                        reporter,
                                    )
                            }
                        }
                    }
                    val vm = wizard
                    if (vm == null) {
                        val state by blockVm.state.collectAsState()
                        BlockScreen(
                            state,
                            BlockActions(
                                onToggleAi = blockVm::toggleAi,
                                onAiText = blockVm::setAiText,
                                onAiSubmit = blockVm::submitAi,
                            ),
                        )
                    } else {
                        val state by vm.state.collectAsState()
                        WizardScreen(
                            state,
                            WizardActions(
                                onNext = vm::next,
                                onMins = vm::setMins,
                                onChangeType = vm::changeType,
                                onReport = vm::reportBadResult,
                            ),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun sentenceDraftEditTurnOn() {
        launch()
        composeRule.onNodeWithText("Or describe it in words").performClick()
        composeRule.onNodeWithText("Social media 45 min a day").performClick()
        composeRule.onNodeWithTag(AI_CREATE_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Set the rule").assertExists()
        composeRule.onNodeWithText("AI draft").assertExists()
        // Nothing is on until the user taps "Turn on block".
        assertTrue(runBlocking { blocks.observeByTarget(BlockTarget.ThisPhone).first() }.isEmpty())

        composeRule.onNodeWithTag(WIZARD_RULER_TAG).performSemanticsAction(SemanticsActions.SetProgress) { it(15f) }
        composeRule.onNodeWithText("Turn on block").performClick()
        composeRule.waitForIdle()

        val saved = runBlocking { blocks.observeByTarget(BlockTarget.ThisPhone).first() }.single()
        assertEquals(BlockSource.AI, saved.source)
        assertEquals(15, saved.limitMins)
        assertTrue(saved.enabled)
    }

    @Test
    fun emptyTextShowsNoWizard() {
        launch()
        composeRule.onNodeWithText("Or describe it in words").performClick()
        composeRule.onNodeWithTag(AI_CREATE_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Set the rule").assertDoesNotExist()
    }

    @Test
    fun reportOpensAConfirmSheetShowingExactlyWhatIsSent() {
        launch()
        composeRule.onNodeWithText("Or describe it in words").performClick()
        composeRule.onNodeWithText("Social media 45 min a day").performClick()
        composeRule.onNodeWithTag(AI_CREATE_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(WIZARD_REPORT_TAG).performClick()
        composeRule.onNodeWithTag(WIZARD_REPORT_SENTENCE_TAG).assertExists()
        composeRule.onNodeWithTag(WIZARD_REPORT_DRAFT_TAG).assertExists()
        composeRule.onNodeWithTag(WIZARD_REPORT_CONFIRM_TAG).performClick()
        composeRule.waitForIdle()

        val report = reporter.reports.single()
        assertEquals("Social media 45 min a day", report.sentence)
    }
}
