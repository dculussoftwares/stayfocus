package com.dculus.stayfocused.feature.block

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.dculus.stayfocused.core.model.BlockDraft
import com.dculus.stayfocused.core.model.BlockSource
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.DaysOfWeek
import com.dculus.stayfocused.core.model.LimitPeriod
import com.dculus.stayfocused.core.model.TimeRange
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.Instant

/** Step 3 of the wizard: editing the rules, the summary, and saving the block. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class WizardRulesViewModelTest {
    @get:Rule
    val main = MainDispatcherRule(UnconfinedTestDispatcher())

    private val blocks = FakeBlockRepository()
    private val clock = TestClock()
    private val devices = FakeLinkedDevicesRepository()

    private fun viewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        failingSave: Boolean = false,
    ) = WizardViewModel(
        handle,
        FakeTargetApps(defaultApps()),
        devices,
        FakeSettingsRepository(),
        if (failingSave) FailingBlocks() else blocks,
        clock,
    )

    private fun WizardViewModel.toRules(type: BlockType) {
        pickType(type)
        next()
    }

    @Test
    fun summaryFollowsTheDraft() {
        val vm = viewModel()
        vm.toRules(BlockType.LIMIT)
        assertEquals("Lock Instagram and YouTube after 30 min a day, on weekdays.", vm.state.value.summary)
        vm.setPeriod(LimitPeriod.HOURLY)
        vm.setMins(15)
        vm.toggleDay(DayOfWeek.SATURDAY)
        assertEquals(
            "Lock Instagram and YouTube after 15 min an hour, on Mon, Tue, Wed, Thu, Fri, Sat.",
            vm.state.value.summary,
        )
    }

    @Test
    fun summaryTruncatesLongAppLists() {
        val vm = viewModel()
        vm.toRules(BlockType.LIMIT)
        vm.toggleApp(pkg("reddit"))
        vm.toggleApp(pkg("x"))
        assertEquals("Lock Instagram, YouTube +2 after 30 min a day, on weekdays.", vm.state.value.summary)
    }

    @Test
    fun cycleDialEditsTheSelectedHalf() {
        val vm = viewModel()
        vm.toRules(BlockType.CYCLE)
        vm.setCycleValue(15)
        vm.selectCycleEdit(CycleEdit.REST)
        vm.setCycleValue(60)
        assertEquals(15, vm.state.value.draft.use)
        assertEquals(60, vm.state.value.draft.rest)
    }

    @Test
    fun pickingATypeResetsTheCycleTab() {
        val vm = viewModel()
        vm.selectCycleEdit(CycleEdit.REST)
        vm.pickType(BlockType.CYCLE)
        assertEquals(CycleEdit.USE, vm.state.value.cycleEdit)
    }

    @Test
    fun daysToggleOnAndOff() {
        val vm = viewModel()
        vm.toggleDay(DayOfWeek.SUNDAY)
        assertTrue(DayOfWeek.SUNDAY in vm.state.value.draft.days)
        vm.toggleDay(DayOfWeek.SUNDAY)
        assertEquals(DaysOfWeek.WEEKDAYS, vm.state.value.draft.days)
    }

    @Test
    fun changeTypeLeavesAiMode() {
        val vm = viewModel(SavedStateHandle())
        vm.toRules(BlockType.LIMIT)
        vm.openAiDraft(BlockDraft(aiNote = "Instagram for 30 minutes a day"))
        assertEquals(true, vm.state.value.draft.fromAi)
        vm.changeType()
        assertEquals(WIZARD_STEP_TYPE, vm.state.value.step)
        assertEquals(false, vm.state.value.draft.fromAi)
    }

    @Test
    fun turnOnSavesAnEnabledLimitBlockAndEmitsTheToast() =
        runTest {
            val vm = viewModel()
            vm.toRules(BlockType.LIMIT)
            vm.setPeriod(LimitPeriod.HOURLY)
            vm.setMins(45)
            vm.events.test {
                vm.next()
                assertEquals(WizardEvent.Saved(deviceId = null, name = "Time limit", targetName = null), awaitItem())
            }
            val b = blocks.observeByTarget(BlockTarget.ThisPhone).first().single()
            assertEquals(BlockType.LIMIT, b.type)
            assertEquals("Time limit", b.name)
            assertEquals(45, b.limitMins)
            assertEquals(LimitPeriod.HOURLY, b.period)
            assertEquals(setOf(pkg("instagram"), pkg("youtube")), b.apps)
            assertTrue(b.enabled)
            assertEquals(BlockSource.MANUAL, b.source)
            assertNull(b.useMins)
            assertNull(b.startedAt)
        }

    @Test
    fun cycleScheduleAndNowStoreOnlyTheirOwnFields() =
        runTest {
            val cycle = viewModel()
            cycle.toRules(BlockType.CYCLE)
            cycle.next()
            val schedule = viewModel()
            schedule.toRules(BlockType.SCHEDULE)
            schedule.setRange(TimeRange.parse("22:00–07:00"))
            schedule.next()
            val all = blocks.observeByTarget(BlockTarget.ThisPhone).first()
            val c = all.single { it.type == BlockType.CYCLE }
            assertEquals(10, c.useMins)
            assertEquals(30, c.restMins)
            assertNull(c.limitMins)
            assertNull(c.range)
            val s = all.single { it.type == BlockType.SCHEDULE }
            assertEquals(TimeRange.parse("22:00–07:00"), s.range)
            assertEquals("Scheduled block", s.name)
            assertEquals(DaysOfWeek.WEEKDAYS, s.days)
        }

    @Test
    fun blockNowStartsAtTheClockTime() =
        runTest {
            val at = Instant.parse("2026-03-04T10:15:00Z")
            clock.set(at)
            val vm = viewModel()
            vm.toRules(BlockType.NOW)
            vm.setNow(90)
            vm.next()
            val b = blocks.observeByTarget(BlockTarget.ThisPhone).first().single()
            assertEquals(BlockType.NOW, b.type)
            assertEquals(90, b.durationMins)
            assertEquals(at, b.startedAt)
            assertEquals("Quick block", b.name)
            assertTrue(b.enabled)
        }

    @Test
    fun templateBlocksAreMarkedAsTemplates() =
        runTest {
            val vm = viewModel(SavedStateHandle(mapOf("prefill" to "bedtime")))
            vm.next()
            assertEquals(
                BlockSource.TEMPLATE,
                blocks
                    .observeByTarget(BlockTarget.ThisPhone)
                    .first()
                    .single()
                    .source,
            )
        }

    @Test
    fun aBlockForAChildPhoneNamesTheDeviceAndIsStoredForIt() =
        runTest {
            devices.devices.value = listOf(device())
            val vm = viewModel(SavedStateHandle(mapOf("target" to "d1", "prefill" to "social_limit")))
            vm.events.test {
                vm.next()
                assertEquals(WizardEvent.Saved("d1", "Time limit", "Aarav's phone"), awaitItem())
            }
            assertEquals(1, blocks.observeByTarget(BlockTarget.Device("d1")).first().size)
        }

    @Test
    fun turnOnTwiceSavesOnce() =
        runTest {
            val vm = viewModel()
            vm.toRules(BlockType.NOW)
            vm.next()
            vm.next()
            assertEquals(1, blocks.observeByTarget(BlockTarget.ThisPhone).first().size)
        }

    @Test
    fun turnOnWithNoAppsAsksForOne() =
        runTest {
            val vm = viewModel(SavedStateHandle(mapOf("prefill" to "social_limit")))
            vm.toggleApp(pkg("instagram"))
            vm.toggleApp(pkg("reddit"))
            vm.toggleApp(pkg("x"))
            vm.events.test {
                vm.next()
                assertEquals(WizardEvent.PickAtLeastOneApp, awaitItem())
            }
            assertTrue(blocks.observeByTarget(BlockTarget.ThisPhone).first().isEmpty())
        }

    @Test
    fun aFailedSaveKeepsTheWizardOpenAndAllowsARetry() =
        runTest {
            val vm = viewModel(failingSave = true)
            vm.toRules(BlockType.NOW)
            vm.events.test {
                vm.next()
                assertEquals(WizardEvent.SaveFailed, awaitItem())
            }
            assertEquals(false, vm.state.value.saving)
            assertEquals(WIZARD_STEP_RULES, vm.state.value.step)
        }
}

private class FailingBlocks : com.dculus.stayfocused.core.data.repository.BlockRepository by FakeBlockRepository() {
    override suspend fun upsert(block: com.dculus.stayfocused.core.model.Block): Unit = error("disk full")
}
