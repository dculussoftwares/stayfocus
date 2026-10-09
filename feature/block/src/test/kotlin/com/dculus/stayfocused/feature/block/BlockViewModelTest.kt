package com.dculus.stayfocused.feature.block

import app.cash.turbine.test
import com.dculus.stayfocused.core.model.AppInfo
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.testing.FakeBlockRepository
import com.dculus.stayfocused.core.testing.FakeLinkedDevicesRepository
import com.dculus.stayfocused.core.testing.FakeLockedAppsRepository
import com.dculus.stayfocused.core.testing.FakeSettingsRepository
import com.dculus.stayfocused.core.testing.MainDispatcherRule
import com.dculus.stayfocused.core.usage.AppUsageStat
import com.dculus.stayfocused.core.usage.DayUsageStats
import com.dculus.stayfocused.core.usage.InstalledAppsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class FakeInstalledApps(
    initial: List<AppInfo>,
) : InstalledAppsRepository {
    val apps = MutableStateFlow(initial)

    override fun observeLaunchableApps(): Flow<List<AppInfo>> = apps
}

@OptIn(ExperimentalCoroutinesApi::class)
class BlockViewModelTest {
    @get:Rule
    val main = MainDispatcherRule(UnconfinedTestDispatcher())

    private val blocks = FakeBlockRepository()
    private val devices = FakeLinkedDevicesRepository()
    private val settings = FakeSettingsRepository()
    private val locked = FakeLockedAppsRepository()
    private val usage = FakeUsageRepository()
    private val installed =
        FakeInstalledApps(
            listOf(
                AppInfo(pkg("instagram"), "Instagram"),
                AppInfo(pkg("youtube"), "YouTube"),
                AppInfo(pkg("chrome"), "Chrome"),
            ),
        )

    private fun TestScope.viewModel(): BlockViewModel {
        val vm = BlockViewModel(blocks, devices, installed, settings, locked, usage)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    @Test
    fun startsOnThisPhoneWithItsBlocksAndActiveCount() =
        runTest {
            blocks.upsert(cycleBlock())
            blocks.upsert(scheduleBlock(enabled = false))
            val vm = viewModel()
            val s = vm.state.value
            assertEquals(BlockTarget.ThisPhone, s.selected)
            assertEquals(listOf("b1", "b3"), s.blocks.map { it.block.id })
            assertEquals(1, s.targets.single().activeCount)
            assertEquals(listOf("Instagram", "YouTube"), s.blocks.first().appLabels)
        }

    @Test
    fun switchingTargetShowsThatDevicesBlocksAndCounts() =
        runTest {
            val kid = BlockTarget.Device("d1")
            devices.devices.value = listOf(device())
            blocks.upsert(cycleBlock())
            blocks.upsert(limitBlock(id = "k1", target = kid))
            blocks.upsert(limitBlock(id = "k2", target = kid, enabled = false))
            val vm = viewModel()

            assertEquals(
                listOf(BlockTarget.ThisPhone, kid),
                vm.state.value.targets
                    .map { it.target },
            )
            assertEquals(
                listOf(1, 1),
                vm.state.value.targets
                    .map { it.activeCount },
            )

            vm.selectTarget(kid)
            val s = vm.state.value
            assertEquals(kid, s.selected)
            assertEquals(listOf("k1", "k2"), s.blocks.map { it.block.id })
            assertEquals("Aarav's phone", s.selectedTarget.device?.name)

            vm.selectTarget(BlockTarget.ThisPhone)
            assertEquals(
                listOf("b1"),
                vm.state.value.blocks
                    .map { it.block.id },
            )
        }

    @Test
    fun selectedDeviceThatGetsUnlinkedFallsBackToThisPhone() =
        runTest {
            devices.devices.value = listOf(device())
            val vm = viewModel()
            vm.selectTarget(BlockTarget.Device("d1"))
            assertEquals(BlockTarget.Device("d1"), vm.state.value.selected)

            devices.devices.value = emptyList()
            assertEquals(BlockTarget.ThisPhone, vm.state.value.selected)

            devices.devices.value = listOf(device())
            assertEquals(BlockTarget.ThisPhone, vm.state.value.selected)
        }

    @Test
    fun newBlockOpensTheWizardForTheSelectedTarget() =
        runTest {
            devices.devices.value = listOf(device())
            val vm = viewModel()
            vm.events.test {
                vm.newBlock()
                assertEquals(BlockEvent.OpenWizard(target = null, prefill = null), awaitItem())
                vm.selectTarget(BlockTarget.Device("d1"))
                vm.newBlock()
                assertEquals(BlockEvent.OpenWizard(target = "d1", prefill = null), awaitItem())
            }
        }

    @Test
    fun templateOpensTheWizardWithItsIdForTheSelectedTarget() =
        runTest {
            devices.devices.value = listOf(device())
            val vm = viewModel()
            vm.selectTarget(BlockTarget.Device("d1"))
            vm.events.test {
                vm.useTemplate(BlockTemplate.BEDTIME)
                assertEquals(BlockEvent.OpenWizard(target = "d1", prefill = "bedtime"), awaitItem())
            }
        }

    @Test
    fun templatePrefillUsesInstalledAppsAndTheSelectedTarget() =
        runTest {
            devices.devices.value = listOf(device())
            val vm = viewModel()
            vm.selectTarget(BlockTarget.Device("d1"))
            val p = vm.templatePrefill(BlockTemplate.MINDFUL_SCROLLING)
            assertEquals(BlockTarget.Device("d1"), p.draft.target)
            assertEquals(setOf(pkg("instagram"), pkg("youtube")), p.draft.apps)
            assertEquals(WIZARD_STEP_RULES, p.startStep)

            installed.apps.value = listOf(AppInfo(pkg("chrome"), "Chrome"))
            val none = vm.templatePrefill(BlockTemplate.MINDFUL_SCROLLING)
            assertTrue(none.draft.apps.isEmpty())
            assertEquals(WIZARD_STEP_APPS, none.startStep)
        }

    @Test
    fun togglingABlockPersists() =
        runTest {
            blocks.upsert(cycleBlock())
            val vm = viewModel()
            vm.setBlockEnabled("b1", false)
            assertFalse(blocks.get("b1")!!.enabled)
            assertEquals(
                0,
                vm.state.value.targets
                    .single()
                    .activeCount,
            )
        }

    @Test
    fun aiPanelOnlyOpensWhenTheFlagIsOn() =
        runTest {
            val vm = viewModel()
            vm.toggleAi()
            assertFalse(vm.state.value.aiAvailable)
            assertFalse(vm.state.value.aiOpen)

            settings.setAiEnabled(true)
            assertTrue(vm.state.value.aiAvailable)
            assertTrue(vm.state.value.aiOpen)
            vm.setAiText("Social media 45 min a day")
            assertEquals("Social media 45 min a day", vm.state.value.aiText)
        }

    @Test
    fun tabSwitches() =
        runTest {
            val vm = viewModel()
            vm.selectTab(BlockTab.ALL_APPS)
            assertEquals(BlockTab.ALL_APPS, vm.state.value.tab)
        }

    @Test
    fun fallbackLabelsForUninstalledApps() {
        assertEquals("Instagram", fallbackLabel(pkg("instagram")))
        assertEquals("X", fallbackLabel(pkg("x")))
        assertEquals("Foo", fallbackLabel("com.example.foo"))
    }

    @Test
    fun allAppsListsLaunchableAppsAZWithTodayUsageAndLockState() =
        runTest {
            usage.today.value =
                usage.today.value.copy(
                    apps = listOf(AppUsageStat(pkg("instagram"), 83 * 60_000L, opens = 12, firstAfterUnlock = 0)),
                )
            val vm = viewModel()
            assertEquals(
                listOf("Chrome", "Instagram", "YouTube"),
                vm.state.value.apps
                    .map { it.label },
            )
            val ig =
                vm.state.value.apps
                    .single { it.pkg == pkg("instagram") }
            assertEquals(83, ig.mins)
            assertEquals(12, ig.opens)
            assertFalse(ig.locked)
            assertEquals(
                0,
                vm.state.value.apps
                    .single { it.pkg == pkg("chrome") }
                    .opens,
            )
        }

    @Test
    fun lockingAndUnlockingAnAppWritesAndRemovesTheLockedApp() =
        runTest {
            val vm = viewModel()
            vm.toggleAppLock(pkg("instagram"))
            assertEquals(listOf(pkg("instagram")), locked.observeAll().first().map { it.pkg })
            assertTrue(
                vm.state.value.apps
                    .single { it.pkg == pkg("instagram") }
                    .locked,
            )
            vm.toggleAppLock(pkg("instagram"))
            assertTrue(locked.observeAll().first().isEmpty())
            assertFalse(
                vm.state.value.apps
                    .single { it.pkg == pkg("instagram") }
                    .locked,
            )
        }

    @Test
    fun quickRepeatedTapsAlternateLockAndUnlock() =
        runTest {
            val vm = viewModel()
            repeat(3) { vm.toggleAppLock(pkg("instagram")) }
            assertEquals(listOf(pkg("instagram")), locked.observeAll().first().map { it.pkg })
            vm.toggleAppLock(pkg("instagram"))
            assertTrue(locked.observeAll().first().isEmpty())
        }

    @Test
    fun lockOnAChildPhoneDoesNotShowOnThisPhone() =
        runTest {
            devices.devices.value = listOf(device())
            locked.lock(pkg("instagram"), BlockTarget.Device("d1"))
            val vm = viewModel()
            assertFalse(
                vm.state.value.apps
                    .single { it.pkg == pkg("instagram") }
                    .locked,
            )
            vm.selectTarget(BlockTarget.Device("d1"))
            assertTrue(
                vm.state.value.apps
                    .isEmpty(),
            )
        }
}
