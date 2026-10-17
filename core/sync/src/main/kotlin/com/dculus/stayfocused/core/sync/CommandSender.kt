package com.dculus.stayfocused.core.sync

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the parent shows for a command it sent. */
enum class DeliveryState {
    /** Sent, no answer yet. */
    PENDING,

    /** No answer for [Delivery.OFFLINE_AFTER_MILLIS]: show "Phone is offline, it'll apply when it's back". */
    OFFLINE,
    DONE,
    FAILED,
    EXPIRED,
}

object Delivery {
    const val OFFLINE_AFTER_MILLIS = 10_000L
}

/**
 * Maps the raw [statuses] of one command to what the parent shows: pending until acknowledged, offline after
 * [offlineAfterMillis] without an answer (a later answer still replaces it), then the final state, where it ends.
 */
fun deliveryStates(
    statuses: Flow<CommandStatus>,
    offlineAfterMillis: Long = Delivery.OFFLINE_AFTER_MILLIS,
): Flow<DeliveryState> =
    channelFlow {
        send(DeliveryState.PENDING)
        val timer =
            launch {
                delay(offlineAfterMillis)
                send(DeliveryState.OFFLINE)
            }
        val final =
            statuses
                .map { status ->
                    when (status) {
                        CommandStatus.PENDING -> null
                        CommandStatus.DONE -> DeliveryState.DONE
                        CommandStatus.FAILED -> DeliveryState.FAILED
                        CommandStatus.EXPIRED -> DeliveryState.EXPIRED
                    }
                }.filterNotNull()
                .first()
        timer.cancel()
        send(final)
    }

/** The parent side: writes a command to a child device and reports how delivery goes. */
class CommandSender
    @Inject
    constructor(
        private val repository: CommandRepository,
    ) {
        /** Queues [command] for [target] and returns its delivery progress; the flow ends at a final state. */
        fun send(
            target: DeviceTarget,
            command: RemoteCommand,
        ): Flow<DeliveryState> {
            val id = repository.send(target, command)
            return deliveryStates(repository.observeStatus(target, id))
        }
    }
