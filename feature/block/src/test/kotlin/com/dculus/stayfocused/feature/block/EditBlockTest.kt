package com.dculus.stayfocused.feature.block

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.testing.FakeBlockRepository
import com.dculus.stayfocused.core.testing.FakeLinkedDevicesRepository
import com.dculus.stayfocused.core.testing.FakeSettingsRepository
import com.dculus.stayfocused.core.testing.MainDispatcherRule
import com.dculus.stayfocused.core.testing.TestClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class EditBlockTest {
    @get:Rule
    val main = MainDispatcherRule(UnconfinedTestDispatcher())

    private val blocks = FakeBlockRepository()

    private fun viewModel(blockId: String): WizardViewModel {
        val handle = SavedStateHandle(mapOf("blockId" to blockId))
        return WizardViewModel(
            handle,
            FakeTargetApps(defaultApps()),
            FakeLinkedDevicesRepository(),
            FakeSettingsRepository(),
            blocks,
            TestClock(),
        )
    }

    @Test
    fun opensAtTheRulesStepWithTheBlocksValues() =
        runTest {
            blocks.upsert(cycleBlock())
            val s = viewModel("b1").state.value
            assertTrue(s.editing)
            assertTrue(s.ready)
            assertEquals(WIZARD_STEP_RULES, s.step)
            assertEquals(10, s.draft.use)
            assertEquals(30, s.draft.rest)
            assertEquals(cycleBlock().apps, s.draft.apps)
        }

    @Test
    fun savingKeepsTheIdCreatedAtEnabledAndSource() =
        runTest {
            val original = limitBlock(enabled = false)
            blocks.upsert(original)
            val vm = viewModel("b2")
            vm.setMins(45)
            vm.next()
            val saved = blocks.get("b2")!!
            assertEquals(original.id, saved.id)
            assertEquals(original.createdAt, saved.createdAt)
            assertEquals(original.source, saved.source)
            assertFalse(saved.enabled)
            assertEquals(45, saved.limitMins)
        }

    @Test
    fun saveEmitsAnEditedEvent() =
        runTest {
            blocks.upsert(limitBlock())
            val vm = viewModel("b2")
            vm.events.test {
                vm.next()
                val e = awaitItem() as WizardEvent.Saved
                assertTrue(e.edited)
                assertNull(e.deviceId)
            }
        }

    @Test
    fun deleteRemovesTheBlockAndLeaves() =
        runTest {
            blocks.upsert(limitBlock())
            val vm = viewModel("b2")
            vm.events.test {
                vm.deleteBlock()
                assertEquals(WizardEvent.Deleted(null), awaitItem())
            }
            assertNull(blocks.get("b2"))
        }

    @Test
    fun aDeviceBlockDeletesBackToItsDevice() =
        runTest {
            blocks.upsert(limitBlock(target = BlockTarget.Device("d1")))
            val handle = SavedStateHandle(mapOf("blockId" to "b2", "target" to "d1"))
            val vm =
                WizardViewModel(
                    handle,
                    FakeTargetApps(defaultApps()),
                    FakeLinkedDevicesRepository(),
                    FakeSettingsRepository(),
                    blocks,
                    TestClock(),
                )
            vm.events.test {
                vm.deleteBlock()
                assertEquals(WizardEvent.Deleted("d1"), awaitItem())
            }
        }

    @Test
    fun anUnchangedRunningBlockNowKeepsItsStartedAt() =
        runTest {
            val started = Instant.parse("2026-01-01T08:00:00Z")
            blocks.upsert(
                limitBlock(id = "n1").copy(
                    type = BlockType.NOW,
                    limitMins = null,
                    period = null,
                    durationMins = 60,
                    startedAt = started,
                ),
            )
            val vm = viewModel("n1")
            vm.next()
            assertEquals(started, blocks.get("n1")!!.startedAt)
            val changed = viewModel("n1")
            changed.setNow(120)
            changed.next()
            assertTrue(blocks.get("n1")!!.startedAt != started)
        }

    @Test
    fun aMissingBlockClosesTheWizard() =
        runTest {
            val vm = viewModel("gone")
            vm.events.test { assertEquals(WizardEvent.Close, awaitItem()) }
        }
}
