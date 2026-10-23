package com.dculus.stayfocused.feature.block

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.dculus.stayfocused.core.model.AppInfo
import com.dculus.stayfocused.core.model.BlockDraft
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.sync.BlockDescriber
import com.dculus.stayfocused.core.testing.FakeLinkedDevicesRepository
import com.dculus.stayfocused.core.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DescribeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule(UnconfinedTestDispatcher())

    private val describer = FakeBlockDescriber()
    private val handoff = AiDraftHandoff()
    private val devices = FakeLinkedDevicesRepository()
    private val installed = FakeInstalled()

    private class FakeInstalled : com.dculus.stayfocused.core.usage.InstalledAppsRepository {
        override fun observeLaunchableApps() =
            kotlinx.coroutines.flow.flowOf(listOf(AppInfo(pkg("instagram"), "Instagram")))
    }

    private fun viewModel(
        target: String? = null,
        describer: BlockDescriber = this.describer,
    ): DescribeViewModel {
        val handle = SavedStateHandle()
        target?.let { handle["target"] = it }
        return DescribeViewModel(handle, installed, devices, describer, handoff)
    }

    @Test
    fun buildNeedsText() {
        val vm = viewModel()
        assertFalse(vm.state.value.canBuild)
        vm.setText("   ")
        assertFalse(vm.state.value.canBuild)
        vm.build()
        assertTrue(describer.sentences.isEmpty())
        vm.setText("YouTube max 1 hour a day")
        assertTrue(vm.state.value.canBuild)
    }

    @Test
    fun buildHandsTheDraftToTheWizardWithoutSavingAnything() =
        runTest {
            val vm = viewModel()
            vm.setText("  Instagram 45 min a day ")
            vm.events.test {
                vm.build()
                assertEquals(DescribeEvent.OpenWizard(target = null), awaitItem())
            }
            assertEquals(listOf("Instagram 45 min a day"), describer.sentences)
            val request = handoff.take()
            assertEquals("Instagram 45 min a day", request?.sentence)
            assertEquals(45, request?.draft?.mins)
            assertFalse(vm.state.value.busy)
            assertFalse(vm.state.value.error)
        }

    @Test
    fun aSecondBuildWhileReadingIsIgnored() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            describer.gate = gate
            val vm = viewModel()
            vm.setText("No YouTube on school days 8am-3pm")
            vm.build()
            assertTrue(vm.state.value.busy)
            vm.build()
            gate.complete(Unit)
            assertEquals(1, describer.sentences.size)
            assertFalse(vm.state.value.busy)
        }

    @Test
    fun aFailureShowsTheInlineErrorAndTypingClearsIt() =
        runTest {
            val failing =
                object : BlockDescriber {
                    override suspend fun build(
                        sentence: String,
                        installed: List<AppInfo>,
                        target: BlockTarget,
                    ): BlockDraft = error("boom")
                }
            val vm = viewModel(describer = failing)
            vm.setText("Block Reddit")
            vm.build()
            assertTrue(vm.state.value.error)
            assertFalse(vm.state.value.busy)
            assertNull(handoff.take())
            vm.setText("Block Reddit for the next 2 hours")
            assertFalse(vm.state.value.error)
        }

    @Test
    fun theRouteTargetNamesTheTag() {
        devices.devices.value = listOf(device(id = "d1", name = "Aarav's phone"))
        assertNull(viewModel().state.value.targetName)
        assertEquals("Aarav's phone", viewModel(target = "d1").state.value.targetName)
    }

    @Test
    fun theTagFlipsThroughLinkedPhonesOrSaysThereIsNone() =
        runTest {
            val vm = viewModel()
            vm.events.test {
                vm.flipTarget()
                assertEquals(DescribeEvent.NoLinkedPhone, awaitItem())
            }
            devices.devices.value = listOf(device(id = "d1", name = "Aarav's phone"))
            vm.flipTarget()
            assertEquals("Aarav's phone", vm.state.value.targetName)
            vm.setText("YouTube max 1 hour a day")
            vm.events.test {
                vm.build()
                assertEquals(DescribeEvent.OpenWizard(target = "d1"), awaitItem())
            }
            vm.flipTarget()
            assertNull(vm.state.value.targetName)
        }
}
