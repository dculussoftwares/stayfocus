package com.dculus.stayfocused.core.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class CommandProcessorTest {
    private val target = DeviceTarget("parent", "device")
    private val now = 1_000_000_000_000L
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)
    private val repo = FakeCommandRepository()
    private val processed = InMemoryProcessedCommands()
    private val handled = mutableListOf<RemoteCommand>()
    private var failing = false

    private val blockHandler =
        object : CommandHandler {
            override val type = CommandType.BLOCK_APP

            override suspend fun handle(command: RemoteCommand) {
                if (failing) error("boom")
                handled += command
            }
        }

    private fun processor() = CommandProcessor(CommandExecutor(setOf(blockHandler)), repo, processed, clock)

    private fun block(
        id: String,
        ageMillis: Long? = 1_000,
        type: String? = "BLOCK_APP",
        payload: Map<String, Any?> = mapOf("pkg" to "a.b"),
    ) = PendingCommand(id, type, payload, ageMillis?.let { now - it })

    @Test
    fun `runs a command once and acknowledges it done`() =
        runTest {
            processor().process(target, block("c1"))
            assertEquals(listOf<RemoteCommand>(RemoteCommand.BlockApp("a.b")), handled)
            assertEquals(listOf("c1" to CommandStatus.DONE), repo.acks)
        }

    @Test
    fun `a redelivered command is acknowledged again but not run again`() =
        runTest {
            val p = processor()
            p.process(target, block("c1"))
            p.process(target, block("c1"))
            assertEquals(1, handled.size)
            assertEquals(listOf("c1" to CommandStatus.DONE, "c1" to CommandStatus.DONE), repo.acks)
        }

    @Test
    fun `a lost acknowledgement is retried without re-running, even in a new processor`() =
        runTest {
            repo.failingAck = true
            processor().process(target, block("c1"))
            assertEquals(emptyList(), repo.acks)
            repo.failingAck = false
            processor().process(target, block("c1"))
            assertEquals(1, handled.size)
            assertEquals(listOf("c1" to CommandStatus.DONE), repo.acks)
        }

    @Test
    fun `a failed command is not retried`() =
        runTest {
            failing = true
            val p = processor()
            p.process(target, block("c1"))
            failing = false
            p.process(target, block("c1"))
            assertEquals(emptyList(), handled)
            assertEquals(listOf("c1" to CommandStatus.FAILED, "c1" to CommandStatus.FAILED), repo.acks)
        }

    @Test
    fun `a command older than 24 hours expires without running`() =
        runTest {
            processor().process(target, block("old", ageMillis = TimeUnit.HOURS.toMillis(24) + 1))
            assertEquals(emptyList(), handled)
            assertEquals(listOf("old" to CommandStatus.EXPIRED), repo.acks)
        }

    @Test
    fun `a stale malformed command is expired, not failed`() =
        runTest {
            val old = TimeUnit.HOURS.toMillis(25)
            processor().process(target, block("a", ageMillis = old, type = "WIPE"))
            processor().process(target, block("b", ageMillis = old, payload = emptyMap()))
            assertEquals(listOf("a", "b").map { it to CommandStatus.EXPIRED }, repo.acks)
        }

    @Test
    fun `a handled command redelivered after 24 hours is expired`() =
        runTest {
            val p = processor()
            p.process(target, block("c1", ageMillis = 1_000))
            p.process(target, block("c1", ageMillis = TimeUnit.HOURS.toMillis(25)))
            assertEquals(1, handled.size)
            assertEquals(listOf("c1" to CommandStatus.DONE, "c1" to CommandStatus.EXPIRED), repo.acks)
        }

    @Test
    fun `minutes outside 1 to 480 or fractional are rejected`() {
        for (bad in listOf(0, -5, 481, 1.5, Long.MAX_VALUE)) {
            assertNull(parseCommand("START_FOCUS", mapOf("mins" to bad)), "mins=$bad")
            assertNull(parseCommand("APPROVE_REQUEST", mapOf("reqId" to "r", "mins" to bad)), "mins=$bad")
        }
        assertEquals(RemoteCommand.StartFocus(480), parseCommand("START_FOCUS", mapOf("mins" to 480L)))
        assertEquals(RemoteCommand.StartFocus(1), parseCommand("START_FOCUS", mapOf("mins" to 1.0)))
    }

    @Test
    fun `a command just under 24 hours old still runs`() =
        runTest {
            processor().process(target, block("edge", ageMillis = TimeUnit.HOURS.toMillis(24)))
            assertEquals(1, handled.size)
        }

    @Test
    fun `a command the server has not stamped yet runs`() =
        runTest {
            processor().process(target, block("new", ageMillis = null))
            assertEquals(1, handled.size)
        }

    @Test
    fun `unknown type, bad payload and missing handler fail`() =
        runTest {
            val p = processor()
            p.process(target, block("u", type = "WIPE"))
            p.process(target, block("b", payload = emptyMap()))
            p.process(target, block("n", type = "STOP_FOCUS", payload = emptyMap()))
            assertEquals(
                listOf("u", "b", "n").map { it to CommandStatus.FAILED },
                repo.acks,
            )
        }

    @Test
    fun `parseCommand round-trips every command`() {
        val all =
            listOf(
                RemoteCommand.BlockApp("a"),
                RemoteCommand.UnblockApp("a"),
                RemoteCommand.StartFocus(25),
                RemoteCommand.StopFocus,
                RemoteCommand.ApproveRequest("r", 10),
                RemoteCommand.DenyRequest("r"),
                RemoteCommand.SyncBlocks,
                RemoteCommand.RemindPermission("usage"),
                RemoteCommand.LockAll,
                RemoteCommand.UnlockAll,
            )
        for (command in all) assertEquals(command, parseCommand(command.type.name, command.payload()))
        assertNull(parseCommand(null, emptyMap()))
    }

    @Test
    fun `delivery turns offline after 10 seconds and ends at the final state`() =
        runTest {
            val statuses = MutableStateFlow(CommandStatus.PENDING)
            val seen = mutableListOf<DeliveryState>()
            val job = launch { deliveryStates(statuses).toList(seen) }
            runCurrent()
            assertEquals(listOf(DeliveryState.PENDING), seen)
            advanceTimeBy(Delivery.OFFLINE_AFTER_MILLIS + 1)
            assertEquals(listOf(DeliveryState.PENDING, DeliveryState.OFFLINE), seen)
            statuses.value = CommandStatus.DONE
            runCurrent()
            assertEquals(listOf(DeliveryState.PENDING, DeliveryState.OFFLINE, DeliveryState.DONE), seen)
            job.join()
        }

    @Test
    fun `an answer before 10 seconds never shows offline`() =
        runTest {
            val statuses = MutableStateFlow(CommandStatus.PENDING)
            val seen = mutableListOf<DeliveryState>()
            val job = launch { deliveryStates(statuses).toList(seen) }
            runCurrent()
            statuses.value = CommandStatus.FAILED
            advanceTimeBy(Delivery.OFFLINE_AFTER_MILLIS * 2)
            assertEquals(listOf(DeliveryState.PENDING, DeliveryState.FAILED), seen)
            job.join()
        }

    @Test
    fun `sender writes a pending command and tracks it to done`() =
        runTest {
            val seen = mutableListOf<DeliveryState>()
            val job = launch { CommandSender(repo).send(target, RemoteCommand.StartFocus(25)).toList(seen) }
            runCurrent()
            val stored = repo.observePending(target).first().single()
            assertEquals("START_FOCUS", stored.type)
            assertEquals(mapOf<String, Any?>("mins" to 25), stored.payload)
            repo.acknowledge(target, stored.id, CommandStatus.DONE)
            runCurrent()
            job.join()
            assertEquals(listOf(DeliveryState.PENDING, DeliveryState.DONE), seen)
        }

    @Test
    fun `a write the server rejects ends as failed, not pending forever`() =
        runTest {
            repo.rejectSends = true
            val seen = CommandSender(repo).send(target, RemoteCommand.StartFocus(25)).toList()
            assertEquals(listOf(DeliveryState.PENDING, DeliveryState.FAILED), seen)
        }

    @Test
    fun `without Firebase a send ends as failed`() =
        runTest {
            val seen = CommandSender(NoCommandRepository).send(target, RemoteCommand.StopFocus).toList()
            assertEquals(listOf(DeliveryState.PENDING, DeliveryState.FAILED), seen)
        }

    @Test
    fun `a denied status listener still lets a rejected write end as failed`() =
        runTest {
            repo.failingStatus = true
            repo.rejectSends = true
            val seen = CommandSender(repo).send(target, RemoteCommand.StopFocus).toList()
            assertEquals(listOf(DeliveryState.PENDING, DeliveryState.FAILED), seen)
        }
}
