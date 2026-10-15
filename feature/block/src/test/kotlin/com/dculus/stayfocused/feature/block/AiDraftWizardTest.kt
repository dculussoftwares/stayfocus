package com.dculus.stayfocused.feature.block

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.dculus.stayfocused.core.model.BlockDraft
import com.dculus.stayfocused.core.model.BlockSource
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.testing.FakeBlockRepository
import com.dculus.stayfocused.core.testing.FakeLinkedDevicesRepository
import com.dculus.stayfocused.core.testing.FakeSettingsRepository
import com.dculus.stayfocused.core.testing.MainDispatcherRule
import com.dculus.stayfocused.core.testing.TestClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiDraftWizardTest {
    @get:Rule
    val main = MainDispatcherRule(UnconfinedTestDispatcher())

    private val blocks = FakeBlockRepository()
    private val handoff = AiDraftHandoff()
    private val reporter = FakeAiReporter()
    private val draft = BlockDraft(type = BlockType.LIMIT, mins = 45, aiNote = "Instagram 45 min a day")

    private fun viewModel(prefill: String? = AI_PREFILL): WizardViewModel {
        val handle = SavedStateHandle()
        prefill?.let { handle["prefill"] = it }
        return WizardViewModel(
            handle,
            FakeTargetApps(defaultApps()),
            FakeLinkedDevicesRepository(),
            FakeSettingsRepository(),
            blocks,
            TestClock(),
            handoff,
            reporter,
        )
    }

    @Test
    fun theDraftOpensOnTheRulesStepUnderTheBannerAndSavesNothingYet() =
        runTest {
            handoff.put(AiDraftRequest("Instagram 45 min a day", draft))
            val vm = viewModel()
            val s = vm.state.value
            assertEquals(WIZARD_STEP_RULES, s.step)
            assertTrue(s.draft.fromAi)
            assertEquals(45, s.draft.mins)
            assertEquals("Instagram 45 min a day", s.aiSentence)
            assertNull(handoff.take())
            assertTrue(blocks.observeByTarget(BlockTarget.ThisPhone).first().isEmpty())
        }

    @Test
    fun sentenceDraftEditThenTurnOnSavesAnAiBlock() =
        runTest {
            handoff.put(AiDraftRequest("Instagram 45 min a day", draft))
            val vm = viewModel()
            vm.setMins(20)
            vm.events.test {
                vm.next()
                assertTrue(awaitItem() is WizardEvent.Saved)
            }
            val saved = blocks.observeByTarget(BlockTarget.ThisPhone).first().single()
            assertEquals(BlockSource.AI, saved.source)
            assertEquals(20, saved.limitMins)
            assertTrue(saved.enabled)
        }

    @Test
    fun changeGoesToStepOneAndTheSavedBlockIsManual() =
        runTest {
            handoff.put(AiDraftRequest("Instagram 45 min a day", draft))
            val vm = viewModel()
            vm.changeType()
            assertEquals(WIZARD_STEP_TYPE, vm.state.value.step)
            assertFalse(vm.state.value.draft.fromAi)
            vm.pickType(BlockType.LIMIT)
            vm.next()
            vm.events.test {
                vm.next()
                assertTrue(awaitItem() is WizardEvent.Saved)
            }
            assertEquals(
                BlockSource.MANUAL,
                blocks
                    .observeByTarget(BlockTarget.ThisPhone)
                    .first()
                    .single()
                    .source,
            )
        }

    @Test
    fun withoutAPendingDraftTheWizardOpensAsUsual() =
        runTest {
            val vm = viewModel()
            assertEquals(WIZARD_STEP_TYPE, vm.state.value.step)
            assertFalse(vm.state.value.draft.fromAi)
        }

    @Test
    fun reportSendsTheSentenceAndTheDraft() =
        runTest {
            handoff.put(AiDraftRequest("Instagram 45 min a day", draft))
            val vm = viewModel()
            // The report carries what the AI returned, not the user's later edits.
            vm.setMins(20)
            vm.events.test {
                vm.reportBadResult()
                assertEquals(WizardEvent.ReportSent, awaitItem())
            }
            val report = reporter.reports.single()
            assertEquals("Instagram 45 min a day", report.sentence)
            assertEquals("LIMIT", report.draftFields["type"])
            assertEquals(45, report.draftFields["mins"])
            assertFalse(vm.state.value.reporting)
        }

    @Test
    fun aFailedReportIsToldToTheUser() =
        runTest {
            reporter.succeeds = false
            handoff.put(AiDraftRequest("Instagram 45 min a day", draft))
            val vm = viewModel()
            vm.events.test {
                vm.reportBadResult()
                assertEquals(WizardEvent.ReportFailed, awaitItem())
            }
        }

    @Test
    fun aManualBlockCannotBeReported() =
        runTest {
            val vm = viewModel(prefill = null)
            vm.reportBadResult()
            assertTrue(reporter.reports.isEmpty())
        }
}
