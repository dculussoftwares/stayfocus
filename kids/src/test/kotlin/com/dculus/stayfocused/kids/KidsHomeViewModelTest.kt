package com.dculus.stayfocused.kids

import androidx.lifecycle.viewModelScope
import com.dculus.stayfocused.core.sync.DeviceLinkState
import com.dculus.stayfocused.core.sync.FakeDeviceUnlinkRepository
import com.dculus.stayfocused.core.sync.FakeKidsHomeRepository
import com.dculus.stayfocused.core.sync.KidsRule
import com.dculus.stayfocused.core.sync.KidsRuleType
import com.dculus.stayfocused.core.sync.UnlinkResult
import com.dculus.stayfocused.core.usage.DayUsageStats
import com.dculus.stayfocused.core.usage.UsageStatsDataSource
import com.dculus.stayfocused.kids.monitoring.LinkReset
import com.dculus.stayfocused.kids.monitoring.LinkWatcher
import com.dculus.stayfocused.kids.monitoring.MonitoringControl
import com.dculus.stayfocused.kids.monitoring.ParentLinkStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class KidsHomeViewModelTest {
    private class MemoryStore : ParentLinkStore {
        override var parentName: String? = "Asha"
        override var parentUid: String? = "parent-1"
        override var deviceId: String? = "device-1"
    }

    private class CountingControl : MonitoringControl {
        var syncs = 0

        override fun sync() {
            syncs++
        }
    }

    private class FixedUsage(
        private val mins: Int,
    ) : UsageStatsDataSource {
        override suspend fun dayUsage(date: LocalDate): DayUsageStats =
            DayUsageStats.empty(date).copy(totalMillis = mins * 60_000L)
    }

    private val dispatcher = StandardTestDispatcher()
    private val store = MemoryStore()
    private val home = FakeKidsHomeRepository()
    private val unlinker = FakeDeviceUnlinkRepository()
    private val control = CountingControl()
    private val clock = Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        KidsHomeViewModel(store, home, LinkWatcher(home), unlinker, FixedUsage(72), LinkReset(store, control) {}, clock)

    /** The view model refreshes forever; every test stops it so nothing is left running. */
    private fun KidsHomeViewModel.stop() = viewModelScope.cancel()

    private fun run() = dispatcher.scheduler.runCurrent()

    @Test
    fun showsTheParentTodaysTotalAndTheRules() {
        val rule = KidsRule("b1", "Instagram", KidsRuleType.Limit, limitMins = 30)
        home.rules.value = listOf(rule)
        val vm = viewModel()
        run()
        val state = vm.state.value
        assertEquals("Asha", state.parentName)
        assertEquals(72, state.todayMins)
        assertEquals(listOf(rule), state.rules)
        assertNull(state.outcome)
        vm.stop()
    }

    @Test
    fun unlinkingAsksFirstThenCallsTheFunctionAndClearsTheLink() {
        val vm = viewModel()
        run()
        vm.askToUnlink()
        assertEquals(true, vm.state.value.confirming)
        assertEquals(emptyList<Pair<String, String?>>(), unlinker.calls)

        vm.confirmUnlink()
        run()

        assertEquals(listOf<Pair<String, String?>>("device-1" to "parent-1"), unlinker.calls)
        assertEquals(HomeOutcome.Unlinked, vm.state.value.outcome)
        assertEquals("Asha", vm.state.value.parentName)
        assertNull(store.parentName)
        assertNull(store.parentUid)
        assertNull(store.deviceId)
        assertEquals(1, control.syncs)
        vm.stop()
    }

    @Test
    fun keepingTheLinkDoesNothing() {
        val vm = viewModel()
        run()
        vm.askToUnlink()
        vm.dismissConfirm()
        assertEquals(false, vm.state.value.confirming)
        assertEquals(emptyList<Pair<String, String?>>(), unlinker.calls)
        assertEquals("Asha", store.parentName)
        vm.stop()
    }

    @Test
    fun aFailedUnlinkKeepsTheLinkAndCanBeRetried() {
        unlinker.result = UnlinkResult.Network
        val vm = viewModel()
        run()
        vm.confirmUnlink()
        run()
        assertEquals(UnlinkError.Network, vm.state.value.error)
        assertNull(vm.state.value.outcome)
        assertEquals("Asha", store.parentName)
        assertEquals(0, control.syncs)

        unlinker.result = UnlinkResult.Unlinked
        vm.confirmUnlink()
        run()
        assertEquals(HomeOutcome.Unlinked, vm.state.value.outcome)
        vm.stop()
    }

    @Test
    fun theParentUnlinkingResetsThePhone() {
        val vm = viewModel()
        run()
        home.link.tryEmit(DeviceLinkState.Linked)
        run()
        assertNull(vm.state.value.outcome)

        home.link.tryEmit(DeviceLinkState.Gone)
        run()

        assertEquals(HomeOutcome.Reset, vm.state.value.outcome)
        assertNull(store.parentName)
        assertEquals(1, control.syncs)
        vm.stop()
    }

    @Test
    fun theDeviceVanishingWhileTheCallIsInFlightFinishesWithoutWaitingForTheReply() {
        unlinker.gate = CompletableDeferred()
        val vm = viewModel()
        run()
        vm.confirmUnlink()
        run()
        assertNull(vm.state.value.outcome)
        // The server reports the device gone while the call is out; the reply never needs to arrive.
        home.link.tryEmit(DeviceLinkState.Gone)
        run()
        assertEquals(HomeOutcome.Unlinked, vm.state.value.outcome)
        assertNull(store.parentName)
        assertEquals(1, control.syncs)
        vm.stop()
    }

    @Test
    fun aLinkClearedElsewhereBeforeConfirmingLeavesQuietly() {
        val vm = viewModel()
        run()
        vm.askToUnlink()
        store.parentUid = null
        store.deviceId = null
        vm.confirmUnlink()
        assertEquals(HomeOutcome.Reset, vm.state.value.outcome)
        assertEquals(emptyList<Pair<String, String?>>(), unlinker.calls)
        vm.stop()
    }

    @Test
    fun aLateResetDoesNotEraseANewLink() {
        val vm = viewModel()
        run()
        // The phone relinked to another parent while this screen's watcher was finishing.
        store.parentUid = "parent-2"
        store.deviceId = "device-2"
        home.link.tryEmit(DeviceLinkState.Gone)
        run()
        assertEquals("parent-2", store.parentUid)
        assertEquals(0, control.syncs)
        vm.stop()
    }

    @Test
    fun aPhoneThatIsNotLinkedGoesStraightBack() {
        store.parentName = null
        store.parentUid = null
        store.deviceId = null
        val vm = viewModel()
        assertEquals(HomeOutcome.Reset, vm.state.value.outcome)
        vm.stop()
    }
}
