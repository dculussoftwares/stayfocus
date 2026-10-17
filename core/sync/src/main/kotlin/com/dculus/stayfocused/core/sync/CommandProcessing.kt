package com.dculus.stayfocused.core.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Runs one kind of command on the child. Handlers plug in through a Hilt multibinding (M8-03, M8-05). */
interface CommandHandler {
    val type: CommandType

    /**
     * Applies [command] idempotently. Delivery is at least once: the process can stop after the effect is applied but
     * before the outcome is stored or acknowledged, so the same command may arrive again. Throws to report failure.
     */
    suspend fun handle(command: RemoteCommand)
}

/** The handler registry: finds the handler for a command. */
class CommandExecutor
    @Inject
    constructor(
        handlers: Set<@JvmSuppressWildcards CommandHandler>,
    ) {
        private val byType = handlers.associateBy { it.type }

        /** Runs [command]; a type without a handler, or a handler that throws, ends as [CommandStatus.FAILED]. */
        @Suppress("TooGenericExceptionCaught")
        suspend fun execute(command: RemoteCommand): CommandStatus {
            val handler =
                byType[command.type] ?: run {
                    Timber.w("No handler for %s", command.type)
                    return CommandStatus.FAILED
                }
            return try {
                handler.handle(command)
                CommandStatus.DONE
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Command %s failed", command.type)
                CommandStatus.FAILED
            }
        }
    }

/** Remembers what became of each command id, so redelivery never runs a command twice. */
interface ProcessedCommands {
    fun statusOf(id: String): CommandStatus?

    fun record(
        id: String,
        status: CommandStatus,
    )
}

/** Process-lifetime memory for tests. */
class InMemoryProcessedCommands : ProcessedCommands {
    private val map = mutableMapOf<String, CommandStatus>()

    override fun statusOf(id: String) = map[id]

    override fun record(
        id: String,
        status: CommandStatus,
    ) {
        map[id] = status
    }
}

/**
 * Takes one pending command through expiry, execution and acknowledgement. Idempotent by command id: a command that
 * was already handled (for example because its acknowledgement was lost offline) is only acknowledged again.
 */
class CommandProcessor
    @Inject
    constructor(
        private val executor: CommandExecutor,
        private val repository: CommandRepository,
        private val processed: ProcessedCommands,
        private val clock: Clock,
    ) {
        private val lock = Mutex()

        @Suppress("TooGenericExceptionCaught")
        suspend fun process(
            target: DeviceTarget,
            command: PendingCommand,
        ) = lock.withLock {
            val status = processed.statusOf(command.id) ?: decide(command).also { processed.record(command.id, it) }
            try {
                repository.acknowledge(target, command.id, status)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Recorded already: the next snapshot delivers it again and only the acknowledgement is retried.
                Timber.w(e, "Acknowledging %s failed; will retry", command.id)
            }
        }

        private suspend fun decide(command: PendingCommand): CommandStatus {
            val created = command.createdAtMillis
            // Age first: a stale command is expired whatever its content.
            if (created != null && clock.millis() - created > MAX_AGE_MILLIS) return CommandStatus.EXPIRED
            val parsed = parseCommand(command.type, command.payload)
            return if (parsed == null) CommandStatus.FAILED else executor.execute(parsed)
        }

        companion object {
            /** A command older than this is marked expired instead of run. */
            val MAX_AGE_MILLIS = TimeUnit.HOURS.toMillis(24)
        }
    }
