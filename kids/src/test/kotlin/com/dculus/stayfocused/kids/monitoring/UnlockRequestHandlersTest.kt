package com.dculus.stayfocused.kids.monitoring

import com.dculus.stayfocused.core.blocking.engine.TemporaryAllowances
import com.dculus.stayfocused.core.model.TemporaryAllowance
import com.dculus.stayfocused.core.sync.CommandExecutor
import com.dculus.stayfocused.core.sync.CommandStatus
import com.dculus.stayfocused.core.sync.DeviceAuth
import com.dculus.stayfocused.core.sync.FakeDeviceBlocksRepository
import com.dculus.stayfocused.core.sync.RemoteCommand
import com.dculus.stayfocused.core.testing.FakeBlockRepository
import com.dculus.stayfocused.core.testing.FakeLockedAppsRepository
import com.dculus.stayfocused.core.testing.FakeSettingsRepository
import com.dculus.stayfocused.core.testing.TestClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class FakeAllowances : TemporaryAllowances {
    val state = MutableStateFlow<List<TemporaryAllowance>>(emptyList())

    override fun observe(): Flow<List<TemporaryAllowance>> = state

    override fun clear() {
        state.value = emptyList()
    }

    override fun grant(
        pkg: String,
        until: Instant,
    ) {
        state.value = state.value + TemporaryAllowance(pkg, until)
    }
}

class UnlockRequestHandlersTest {
    private val clock = TestClock()
    private val requests = FakePendingRequestStore()
    private val allowances = FakeAllowances()
    private val links =
        object : ParentLinkStore {
            override var parentName: String? = "Asha"
            override var parentUid: String? = "parent-1"
            override var deviceId: String? = "device-1"
        }
    private val signedIn =
        object : DeviceAuth {
            override val uid = flowOf<String?>("child")

            override suspend fun ensureSignedIn() = "child"
        }
    private val mirror =
        ParentRulesMirror(
            FakeDeviceBlocksRepository(),
            signedIn,
            FakeBlockRepository(),
            FakeLockedAppsRepository(),
            FakeSettingsRepository(),
            links,
        )
    private val executor =
        CommandExecutor(
            setOf(
                ApproveRequestHandler(requests, allowances, mirror, clock),
                DenyRequestHandler(requests, mirror),
            ),
        )

    @Test
    fun approvingOpensTheAppForTheApprovedMinutes() =
        runTest {
            requests.remember("r1", "com.instagram.android")
            val status = executor.execute(RemoteCommand.ApproveRequest("r1", 30))
            assertEquals(CommandStatus.DONE, status)
            assertEquals(
                listOf(TemporaryAllowance("com.instagram.android", clock.instant().plus(Duration.ofMinutes(30)))),
                allowances.state.value,
            )
            assertNull(requests.packageOf("r1"))
        }

    @Test
    fun anUnknownRequestFailsAndUnlocksNothing() =
        runTest {
            assertEquals(CommandStatus.FAILED, executor.execute(RemoteCommand.ApproveRequest("nope", 15)))
            assertEquals(emptyList(), allowances.state.value)
        }

    @Test
    fun denyingForgetsTheRequestAndUnlocksNothing() =
        runTest {
            requests.remember("r1", "com.instagram.android")
            assertEquals(CommandStatus.DONE, executor.execute(RemoteCommand.DenyRequest("r1")))
            assertNull(requests.packageOf("r1"))
            assertEquals(emptyList(), allowances.state.value)
        }

    @Test
    fun anApprovalAfterTheUnlinkFails() =
        runTest {
            requests.remember("r1", "com.instagram.android")
            links.parentUid = null
            assertEquals(CommandStatus.FAILED, executor.execute(RemoteCommand.ApproveRequest("r1", 15)))
            assertEquals(emptyList(), allowances.state.value)
        }
}
