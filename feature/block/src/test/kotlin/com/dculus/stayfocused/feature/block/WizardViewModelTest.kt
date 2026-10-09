package com.dculus.stayfocused.feature.block

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.DaysOfWeek
import com.dculus.stayfocused.core.testing.FakeBlockRepository
import com.dculus.stayfocused.core.testing.FakeLinkedDevicesRepository
import com.dculus.stayfocused.core.testing.FakeSettingsRepository
import com.dculus.stayfocused.core.testing.MainDispatcherRule
import com.dculus.stayfocused.core.testing.TestClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class FakeTargetApps(
    initial: List<TargetApp>,
) : TargetAppsProvider {
    val apps = MutableStateFlow(initial)

    override fun apps(target: BlockTarget): Flow<List<TargetApp>> = apps
}

internal fun defaultApps() =
    listOf(
        TargetApp(pkg("instagram"), "Instagram", 83),
        TargetApp(pkg("youtube"), "YouTube", 0),
        TargetApp(pkg("reddit"), "Reddit", 12),
        TargetApp(pkg("x"), "X", 5),
        TargetApp(pkg("chrome"), "Chrome", 40),
    )

@OptIn(ExperimentalCoroutinesApi::class)
class WizardViewModelTest {
    @get:Rule
    val main = MainDispatcherRule(UnconfinedTestDispatcher())

    private val apps = FakeTargetApps(defaultApps())
    private val devices = FakeLinkedDevicesRepository()
    private val settings = FakeSettingsRepository()
    private val blocks = FakeBlockRepository()
    private val clock = TestClock()

    private fun viewModel(
        target: String? = null,
        prefill: String? = null,
    ): WizardViewModel {
        val handle = SavedStateHandle()
        target?.let { handle["target"] = it }
        prefill?.let { handle["prefill"] = it }
        return WizardViewModel(handle, apps, devices, settings, blocks, clock)
    }

    @Test
    fun defaultsMatchThePrototypeOpenWizard() {
        val s = viewModel().state.value
        assertTrue(s.ready)
        assertEquals(WIZARD_STEP_TYPE, s.step)
        assertEquals(BlockType.LIMIT, s.draft.type)
        assertEquals(setOf(pkg("instagram"), pkg("youtube")), s.draft.apps)
        assertEquals(30, s.draft.mins)
        assertEquals(DaysOfWeek.WEEKDAYS, s.draft.days)
        assertEquals(BlockTarget.ThisPhone, s.draft.target)
    }

    @Test
    fun defaultAppsThatAreNotInstalledAreDropped() {
        apps.apps.value = defaultApps().filter { it.pkg != pkg("youtube") }
        assertEquals(
            setOf(pkg("instagram")),
            viewModel()
                .state.value.draft.apps,
        )
    }

    @Test
    fun routeTargetBecomesTheDraftTarget() {
        assertEquals(
            BlockTarget.Device("d1"),
            viewModel(target = "d1")
                .state.value.draft.target,
        )
    }

    @Test
    fun templatePrefillFillsTheDraftAndOpensTheRulesStep() {
        val s = viewModel(prefill = "mindful_scrolling").state.value
        assertEquals(WIZARD_STEP_RULES, s.step)
        assertEquals(BlockType.CYCLE, s.draft.type)
        assertEquals(10, s.draft.use)
        assertEquals(30, s.draft.rest)
        assertEquals(setOf(pkg("instagram"), pkg("youtube"), pkg("reddit")), s.draft.apps)
    }

    @Test
    fun templateWithNoInstalledAppsOpensTheAppPicker() {
        apps.apps.value = listOf(TargetApp(pkg("chrome"), "Chrome", 0))
        val s = viewModel(prefill = "social_limit").state.value
        assertEquals(WIZARD_STEP_APPS, s.step)
        assertTrue(s.draft.apps.isEmpty())
    }

    @Test
    fun unknownPrefillIsIgnored() {
        val s = viewModel(prefill = "nope").state.value
        assertEquals(WIZARD_STEP_TYPE, s.step)
        assertEquals(BlockType.LIMIT, s.draft.type)
    }

    @Test
    fun pickingATypeSelectsItAndGoesToTheApps() {
        val vm = viewModel()
        vm.pickType(BlockType.SCHEDULE)
        assertEquals(BlockType.SCHEDULE, vm.state.value.draft.type)
        assertEquals(WIZARD_STEP_APPS, vm.state.value.step)
    }

    @Test
    fun togglingAppsAddsAndRemovesThem() {
        val vm = viewModel()
        vm.toggleApp(pkg("reddit"))
        assertEquals(3, vm.state.value.selectedCount)
        vm.toggleApp(pkg("instagram"))
        assertEquals(setOf(pkg("youtube"), pkg("reddit")), vm.state.value.draft.apps)
    }

    @Test
    fun continueAdvancesStepByStepAndStopsAtTheRules() {
        val vm = viewModel()
        vm.next()
        assertEquals(WIZARD_STEP_APPS, vm.state.value.step)
        vm.next()
        assertEquals(WIZARD_STEP_RULES, vm.state.value.step)
        vm.next()
        assertEquals(WIZARD_STEP_RULES, vm.state.value.step)
    }

    @Test
    fun continuingWithNoAppsShowsTheToastAndStays() =
        kotlinx.coroutines.test.runTest {
            val vm = viewModel()
            vm.pickType(BlockType.LIMIT)
            vm.toggleApp(pkg("instagram"))
            vm.toggleApp(pkg("youtube"))
            vm.events.test {
                vm.next()
                assertEquals(WizardEvent.PickAtLeastOneApp, awaitItem())
            }
            assertEquals(WIZARD_STEP_APPS, vm.state.value.step)
        }

    @Test
    fun backGoesToThePreviousStepThenCloses() =
        kotlinx.coroutines.test.runTest {
            val vm = viewModel(prefill = "bedtime")
            assertEquals(WIZARD_STEP_RULES, vm.state.value.step)
            vm.back()
            assertEquals(WIZARD_STEP_APPS, vm.state.value.step)
            vm.back()
            assertEquals(WIZARD_STEP_TYPE, vm.state.value.step)
            vm.events.test {
                vm.back()
                assertEquals(WizardEvent.Close, awaitItem())
            }
        }

    @Test
    fun describeWithAiEmitsAnEvent() =
        kotlinx.coroutines.test.runTest {
            val vm = viewModel()
            vm.events.test {
                vm.describeWithAi()
                assertEquals(WizardEvent.DescribeWithAi, awaitItem())
            }
        }

    @Test
    fun linkedDevicesAreListedButDisabled() {
        devices.devices.value = listOf(device())
        val targets = viewModel().state.value.targets
        assertEquals(listOf(BlockTarget.ThisPhone, BlockTarget.Device("d1")), targets.map { it.target })
        assertEquals(listOf(true, false), targets.map { it.enabled })
    }

    @Test
    fun appListUpdatesKeepTheSelection() {
        val vm = viewModel()
        vm.toggleApp(pkg("reddit"))
        apps.apps.value = defaultApps() + TargetApp("com.example.new", "New", 1)
        assertEquals(6, vm.state.value.apps.size)
        assertFalse(pkg("reddit") !in vm.state.value.draft.apps)
    }

    @Test
    fun uninstalledAppsLeaveTheSelection() {
        val vm = viewModel()
        apps.apps.value = defaultApps().filter { it.pkg != pkg("youtube") }
        assertEquals(setOf(pkg("instagram")), vm.state.value.draft.apps)
    }

    @Test
    fun enabledPillRetargetsTheDraftAndDisabledOnesAreIgnored() {
        devices.devices.value = listOf(device())
        val vm = viewModel(target = "d1")
        vm.selectTarget(BlockTarget.Device("d1"))
        assertEquals(BlockTarget.Device("d1"), vm.state.value.draft.target)
        vm.selectTarget(BlockTarget.ThisPhone)
        assertEquals(BlockTarget.ThisPhone, vm.state.value.draft.target)
        assertEquals(setOf(pkg("instagram"), pkg("youtube")), vm.state.value.draft.apps)
    }

    @Test
    fun aiShortcutFollowsTheAiSetting() =
        kotlinx.coroutines.test.runTest {
            val vm = viewModel()
            assertFalse(vm.state.value.aiAvailable)
            settings.setAiEnabled(true)
            assertTrue(vm.state.value.aiAvailable)
        }

    @Test
    fun continueIsIgnoredWhileLoading() {
        val pending = MutableSharedFlow<List<TargetApp>>()
        val loading =
            object : TargetAppsProvider {
                override fun apps(target: BlockTarget): Flow<List<TargetApp>> = pending
            }
        val vm = WizardViewModel(SavedStateHandle(), loading, devices, settings, blocks, clock)
        assertFalse(vm.state.value.ready)
        vm.next()
        assertEquals(WIZARD_STEP_TYPE, vm.state.value.step)
    }

    @Test
    fun retargetingATemplateKeepsItsAppsTypeAndStep() {
        devices.devices.value = listOf(device())
        val perTarget =
            object : TargetAppsProvider {
                override fun apps(target: BlockTarget): Flow<List<TargetApp>> =
                    MutableStateFlow(if (target == BlockTarget.ThisPhone) defaultApps() else emptyList())
            }
        val handle = SavedStateHandle(mapOf("target" to "d1", "prefill" to "social_limit"))
        val vm = WizardViewModel(handle, perTarget, devices, settings, blocks, clock)
        assertEquals(WIZARD_STEP_APPS, vm.state.value.step)
        vm.pickType(BlockType.NOW)
        vm.selectTarget(BlockTarget.ThisPhone)
        assertEquals(BlockType.NOW, vm.state.value.draft.type)
        assertEquals(WIZARD_STEP_APPS, vm.state.value.step)
        assertEquals(setOf(pkg("instagram"), pkg("reddit"), pkg("x")), vm.state.value.draft.apps)
        assertEquals(BlockTarget.ThisPhone, vm.state.value.draft.target)
    }
}
