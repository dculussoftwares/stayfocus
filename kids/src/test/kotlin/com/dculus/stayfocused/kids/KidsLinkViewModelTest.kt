package com.dculus.stayfocused.kids

import com.dculus.stayfocused.core.sync.DeviceAuth
import com.dculus.stayfocused.core.sync.FakeLinkTokenRepository
import com.dculus.stayfocused.core.sync.LinkTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

@OptIn(ExperimentalCoroutinesApi::class)
class KidsLinkViewModelTest {
    private class FakeAuth(
        private val result: String? = "child-uid",
    ) : DeviceAuth {
        override val uid: Flow<String?> = MutableStateFlow(result)

        override suspend fun ensureSignedIn(): String? = result
    }

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.clock() =
        object : Clock() {
            override fun getZone() = ZoneOffset.UTC

            override fun withZone(zone: java.time.ZoneId) = this

            override fun instant(): Instant = Instant.ofEpochMilli(testScheduler.currentTime + START)
        }

    private fun TestScope.viewModel(
        repository: FakeLinkTokenRepository,
        auth: DeviceAuth = FakeAuth(),
    ) = KidsLinkViewModel(repository, auth, clock())

    @Test
    fun showsTheQrAndCountsDown() =
        runTest(dispatcher) {
            val repository = FakeLinkTokenRepository(clock())
            val vm = viewModel(repository)
            runCurrent()
            val waiting = assertIs<KidsLinkState.Waiting>(vm.state.value)
            assertEquals(LinkTokens.uri(repository.created.single().token), waiting.uri)
            assertEquals("4:55", waiting.remaining)
            advanceTimeBy(3_001)
            assertEquals("4:52", assertIs<KidsLinkState.Waiting>(vm.state.value).remaining)
        }

    @Test
    fun expiryPublishesANewToken() =
        runTest(dispatcher) {
            val repository = FakeLinkTokenRepository(clock())
            val vm = viewModel(repository)
            runCurrent()
            val first = repository.created.single()
            advanceTimeBy(LinkTokens.TTL_MILLIS)
            runCurrent()
            assertEquals(2, repository.created.size)
            assertNotEquals(first.token, repository.created.last().token)
            assertEquals(
                LinkTokens.uri(repository.created.last().token),
                assertIs<KidsLinkState.Waiting>(vm.state.value).uri,
            )
        }

    @Test
    fun claimingTheTokenMovesToConsent() =
        runTest(dispatcher) {
            val repository = FakeLinkTokenRepository(clock())
            val vm = viewModel(repository)
            runCurrent()
            repository.claim(repository.created.single().token)
            runCurrent()
            assertEquals(KidsLinkState.Claimed, vm.state.value)
        }

    @Test
    fun failureOffersARetryThatWorks() =
        runTest(dispatcher) {
            val repository = FakeLinkTokenRepository(clock()).apply { failCreate = true }
            val vm = viewModel(repository)
            runCurrent()
            assertEquals(KidsLinkState.Failed, vm.state.value)
            repository.failCreate = false
            vm.retry()
            runCurrent()
            assertIs<KidsLinkState.Waiting>(vm.state.value)
        }

    @Test
    fun noIdentityIsAFailureToo() =
        runTest(dispatcher) {
            val vm = viewModel(FakeLinkTokenRepository(clock()), FakeAuth(result = null))
            runCurrent()
            assertEquals(KidsLinkState.Failed, vm.state.value)
        }

    private companion object {
        const val START = 1_700_000_000_000L
    }
}
