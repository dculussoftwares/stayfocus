package com.dculus.stayfocused.kids

import androidx.lifecycle.SavedStateHandle
import com.dculus.stayfocused.core.sync.ApproveOutcome
import com.dculus.stayfocused.core.sync.ConsentInfo
import com.dculus.stayfocused.core.sync.FakeLinkHandshakeRepository
import com.dculus.stayfocused.core.sync.LinkFailure
import com.dculus.stayfocused.kids.monitoring.MonitoringControl
import com.dculus.stayfocused.kids.monitoring.ParentLinkStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class KidsConsentViewModelTest {
    private class MemoryStore : ParentLinkStore {
        override var parentName: String? = null
        override var parentUid: String? = null
        override var deviceId: String? = null
    }

    private class CountingControl : MonitoringControl {
        var syncs = 0

        override fun sync() {
            syncs++
        }
    }

    private val dispatcher = StandardTestDispatcher()
    private val handshake = FakeLinkHandshakeRepository()
    private val store = MemoryStore()
    private val control = CountingControl()
    private val clock = Clock.fixed(Instant.ofEpochMilli(0), ZoneOffset.UTC)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        KidsConsentViewModel(SavedStateHandle(mapOf("token" to TOKEN)), handshake, store, control, clock)

    @Test
    fun showsTheServersCodeAndTheParentsNameOnceTheClaimIsSeen() =
        runTest(dispatcher) {
            val vm = viewModel()
            runCurrent()
            assertNull(vm.state.value.code)

            handshake.pushConsent(ConsentInfo("482913", "Asha"))
            runCurrent()
            assertEquals("482913", vm.state.value.code)
            assertEquals("Asha", vm.state.value.parentName)
        }

    @Test
    fun aParentWithoutANameIsLeftToTheScreenToWord() =
        runTest(dispatcher) {
            handshake.pushConsent(ConsentInfo("482913", ""))
            val vm = viewModel()
            runCurrent()
            assertNull(vm.state.value.parentName)
        }

    @Test
    fun allowingStoresTheLinkAndStartsMonitoring() =
        runTest(dispatcher) {
            handshake.pushConsent(ConsentInfo("482913", "Asha"))
            handshake.answerResult = ApproveOutcome.Linked("parent-1", "device-9", "Asha")
            val vm = viewModel()
            runCurrent()

            vm.allow("your parent")
            runCurrent()

            assertEquals(listOf(TOKEN to true), handshake.answers)
            assertEquals("parent-1", store.parentUid)
            assertEquals("device-9", store.deviceId)
            assertEquals("Asha", store.parentName)
            assertEquals(1, control.syncs)
            assertEquals(ConsentOutcome.Linked, vm.state.value.outcome)
        }

    @Test
    fun aNamelessParentIsStoredUnderTheFallbackSoMonitoringStarts() =
        runTest(dispatcher) {
            handshake.pushConsent(ConsentInfo("482913", ""))
            handshake.answerResult = ApproveOutcome.Linked("parent-1", "device-9", "")
            val vm = viewModel()
            runCurrent()
            vm.allow("your parent")
            runCurrent()
            assertEquals("your parent", store.parentName)
        }

    @Test
    fun cancellingTellsTheParentNoAndShowsANewCode() =
        runTest(dispatcher) {
            handshake.pushConsent(ConsentInfo("482913", "Asha"))
            handshake.answerResult = ApproveOutcome.Declined
            val vm = viewModel()
            runCurrent()

            vm.cancel()
            runCurrent()

            assertEquals(listOf(TOKEN to false), handshake.answers)
            assertEquals(ConsentOutcome.ShowNewCode, vm.state.value.outcome)
            assertNull(store.parentName)
            assertEquals(0, control.syncs)
        }

    @Test
    fun anExpiredCodeLinksNothingAndOffersANewOne() =
        runTest(dispatcher) {
            handshake.pushConsent(ConsentInfo("482913", "Asha"))
            handshake.answerResult = ApproveOutcome.Failed(LinkFailure.Expired)
            val vm = viewModel()
            runCurrent()
            vm.allow("your parent")
            runCurrent()

            assertEquals(ConsentError.Expired, vm.state.value.error)
            assertNull(vm.state.value.outcome)
            assertNull(store.parentName)
            assertEquals(0, control.syncs)
            vm.showNewCode()
            assertEquals(ConsentOutcome.ShowNewCode, vm.state.value.outcome)
        }

    @Test
    fun aFailedDeclineKeepsTheScreenSoTheChildIsNotLeftBelievingItWasCancelled() =
        runTest(dispatcher) {
            handshake.pushConsent(ConsentInfo("482913", "Asha"))
            handshake.answerResult = ApproveOutcome.Failed(LinkFailure.Network)
            val vm = viewModel()
            runCurrent()
            vm.cancel()
            runCurrent()
            assertNull(vm.state.value.outcome)
            assertEquals(ConsentError.Network, vm.state.value.error)
            assertEquals(false, vm.state.value.working)
        }

    @Test
    fun theExpiryTimerNeverInterruptsAnApprovalInFlight() =
        runTest(dispatcher) {
            handshake.pushConsent(ConsentInfo("482913", "Asha", expiresAtMillis = 60_000))
            handshake.answerResult = ApproveOutcome.Linked("parent-1", "device-9", "Asha")
            val vm = viewModel()
            runCurrent()
            vm.allow("your parent")
            advanceTimeBy(80_000)
            runCurrent()
            assertEquals(ConsentOutcome.Linked, vm.state.value.outcome)
            assertNull(vm.state.value.error)
        }

    @Test
    fun retryingKeepsWatchingTheSameTokenAfterATimeout() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceTimeBy(16_000)
            runCurrent()
            assertEquals(ConsentError.Network, vm.state.value.error)

            vm.retryConsent()
            handshake.pushConsent(ConsentInfo("482913", "Asha"))
            runCurrent()
            assertEquals("482913", vm.state.value.code)
            assertNull(vm.state.value.error)
        }

    @Test
    fun noConnectionKeepsTheScreenSoTheChildCanTryAgain() =
        runTest(dispatcher) {
            handshake.pushConsent(ConsentInfo("482913", "Asha"))
            handshake.answerResult = ApproveOutcome.Failed(LinkFailure.Network)
            val vm = viewModel()
            runCurrent()
            vm.allow("your parent")
            runCurrent()
            assertEquals(ConsentError.Network, vm.state.value.error)
            assertEquals(false, vm.state.value.working)

            handshake.answerResult = ApproveOutcome.Linked("parent-1", "device-9", "Asha")
            vm.allow("your parent")
            runCurrent()
            assertEquals(ConsentOutcome.Linked, vm.state.value.outcome)
            assertNull(vm.state.value.error)
        }

    @Test
    fun allowingIsWithdrawnWhenTheTokenRunsOut() =
        runTest(dispatcher) {
            handshake.pushConsent(ConsentInfo("482913", "Asha", expiresAtMillis = 60_000))
            val vm = viewModel()
            runCurrent()
            assertNull(vm.state.value.error)
            advanceTimeBy(71_000)
            runCurrent()
            assertEquals(ConsentError.Expired, vm.state.value.error)
        }

    @Test
    fun noClaimWithinTheWaitIsAConnectionProblemNotAnExpiredCode() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceTimeBy(16_000)
            runCurrent()
            assertEquals(ConsentError.Network, vm.state.value.error)
            assertNull(vm.state.value.code)
        }

    @Test
    fun allowDoesNothingBeforeTheCodeIsShown() =
        runTest(dispatcher) {
            val vm = viewModel()
            runCurrent()
            vm.allow("your parent")
            runCurrent()
            assertEquals(emptyList(), handshake.answers)
        }

    private companion object {
        const val TOKEN = "abcdefghijklmnopqrstuv"
    }
}
