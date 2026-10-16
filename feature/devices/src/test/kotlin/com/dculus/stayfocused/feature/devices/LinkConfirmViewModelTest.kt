package com.dculus.stayfocused.feature.devices

import androidx.lifecycle.SavedStateHandle
import com.dculus.stayfocused.core.sync.ClaimOutcome
import com.dculus.stayfocused.core.sync.ClaimedLink
import com.dculus.stayfocused.core.sync.FakeLinkHandshakeRepository
import com.dculus.stayfocused.core.sync.LinkAnswer
import com.dculus.stayfocused.core.sync.LinkFailure
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

@OptIn(ExperimentalCoroutinesApi::class)
class LinkConfirmViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.ofEpochMilli(0), ZoneOffset.UTC)
    private val link = ClaimedLink(code = "482913", model = "Pixel 7", deviceId = "dev-1", expiresAtMillis = 60_000)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(handshake: FakeLinkHandshakeRepository) =
        LinkConfirmViewModel(SavedStateHandle(mapOf("token" to TOKEN)), handshake, clock)

    private fun handshake() = FakeLinkHandshakeRepository().apply { claimResult = ClaimOutcome.Claimed(link) }

    @Test
    fun claimsTheScannedTokenAndShowsTheServersCodeWithTheModelAsTheName() =
        runTest(dispatcher) {
            val handshake = handshake()
            val vm = viewModel(handshake)
            assertEquals(LinkConfirmState.Claiming, vm.state.value)
            runCurrent()
            assertEquals(LinkConfirmState.Waiting("482913"), vm.state.value)
            assertEquals("Pixel 7", vm.name.value)
            assertEquals(listOf<Pair<String, String?>>(TOKEN to null), handshake.claims)
        }

    @Test
    fun theChildAllowingLinksTheNewPhone() =
        runTest(dispatcher) {
            val handshake = handshake()
            val vm = viewModel(handshake)
            runCurrent()
            handshake.pushAnswer(LinkAnswer.Linked)
            runCurrent()
            assertEquals(LinkConfirmState.Linked("dev-1"), vm.state.value)
        }

    @Test
    fun theChildCancellingEndsTheWait() =
        runTest(dispatcher) {
            val handshake = handshake()
            val vm = viewModel(handshake)
            runCurrent()
            handshake.pushAnswer(LinkAnswer.Declined)
            runCurrent()
            assertEquals(LinkConfirmState.Declined, vm.state.value)
        }

    @Test
    fun aChildWhoNeverAnswersTimesOutWhenTheTokenExpires() =
        runTest(dispatcher) {
            val vm = viewModel(handshake())
            runCurrent()
            advanceTimeBy(59_000)
            runCurrent()
            assertEquals(LinkConfirmState.Waiting("482913"), vm.state.value)
            advanceTimeBy(1_001)
            runCurrent()
            assertEquals(LinkConfirmState.Failed(LinkFailure.Expired), vm.state.value)
        }

    @Test
    fun aRefusedClaimShowsWhyAndRetryClaimsAgain() =
        runTest(dispatcher) {
            val handshake =
                FakeLinkHandshakeRepository().apply {
                    claimResult =
                        ClaimOutcome.Failed(
                            LinkFailure.Network,
                        )
                }
            val vm = viewModel(handshake)
            runCurrent()
            assertEquals(LinkConfirmState.Failed(LinkFailure.Network), vm.state.value)

            handshake.claimResult = ClaimOutcome.Claimed(link)
            vm.retry()
            runCurrent()
            assertEquals(LinkConfirmState.Waiting("482913"), vm.state.value)
            assertEquals(2, handshake.claims.size)
        }

    @Test
    fun renamingSendsOnlyTheLastNameAfterTheTypingPauses() =
        runTest(dispatcher) {
            val handshake = handshake()
            val vm = viewModel(handshake)
            runCurrent()
            vm.onNameChange("A")
            advanceTimeBy(100)
            vm.onNameChange("Aarav's phone")
            advanceTimeBy(500)
            runCurrent()
            assertEquals("Aarav's phone", vm.name.value)
            assertEquals(listOf<Pair<String, String?>>(TOKEN to null, TOKEN to "Aarav's phone"), handshake.claims)
        }

    @Test
    fun aBlankNameIsNotSent() =
        runTest(dispatcher) {
            val handshake = handshake()
            val vm = viewModel(handshake)
            runCurrent()
            vm.onNameChange("   ")
            advanceTimeBy(500)
            runCurrent()
            assertEquals(1, handshake.claims.size)
        }

    private companion object {
        const val TOKEN = "abcdefghijklmnopqrstuv"
    }
}
