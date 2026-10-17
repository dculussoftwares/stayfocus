package com.dculus.stayfocused.core.sync

/** The kinds of command a parent can send; the names are the Firestore `type` values the rules accept. */
enum class CommandType {
    BLOCK_APP,
    UNBLOCK_APP,
    START_FOCUS,
    STOP_FOCUS,
    APPROVE_REQUEST,
    DENY_REQUEST,
    SYNC_BLOCKS,
    REMIND_PERMISSION,
}

/** A parent to child command with its fixed payload (`users/{uid}/devices/{id}/commands/{cmdId}`). */
sealed interface RemoteCommand {
    val type: CommandType

    /** The Firestore `payload` map; keys match what `firestore.rules` allows for [type]. */
    fun payload(): Map<String, Any>

    data class BlockApp(
        val pkg: String,
    ) : RemoteCommand {
        override val type get() = CommandType.BLOCK_APP

        override fun payload() = mapOf("pkg" to pkg)
    }

    data class UnblockApp(
        val pkg: String,
    ) : RemoteCommand {
        override val type get() = CommandType.UNBLOCK_APP

        override fun payload() = mapOf("pkg" to pkg)
    }

    data class StartFocus(
        val mins: Int,
    ) : RemoteCommand {
        override val type get() = CommandType.START_FOCUS

        override fun payload() = mapOf("mins" to mins)
    }

    data object StopFocus : RemoteCommand {
        override val type get() = CommandType.STOP_FOCUS

        override fun payload() = emptyMap<String, Any>()
    }

    data class ApproveRequest(
        val reqId: String,
        val mins: Int,
    ) : RemoteCommand {
        override val type get() = CommandType.APPROVE_REQUEST

        override fun payload() = mapOf("reqId" to reqId, "mins" to mins)
    }

    data class DenyRequest(
        val reqId: String,
    ) : RemoteCommand {
        override val type get() = CommandType.DENY_REQUEST

        override fun payload() = mapOf("reqId" to reqId)
    }

    data object SyncBlocks : RemoteCommand {
        override val type get() = CommandType.SYNC_BLOCKS

        override fun payload() = emptyMap<String, Any>()
    }

    data class RemindPermission(
        val permission: String,
    ) : RemoteCommand {
        override val type get() = CommandType.REMIND_PERMISSION

        override fun payload() = mapOf("permission" to permission)
    }
}

/** Reads a command back from Firestore data. Null for an unknown type or a payload that does not fit the type. */
@Suppress("CyclomaticComplexMethod")
fun parseCommand(
    type: String?,
    payload: Map<String, Any?>,
): RemoteCommand? {
    val kind = CommandType.entries.firstOrNull { it.name == type } ?: return null
    val p = PayloadReader(payload)
    return when (kind) {
        CommandType.BLOCK_APP -> p.str("pkg")?.let { RemoteCommand.BlockApp(it) }
        CommandType.UNBLOCK_APP -> p.str("pkg")?.let { RemoteCommand.UnblockApp(it) }
        CommandType.START_FOCUS -> p.int("mins")?.let { RemoteCommand.StartFocus(it) }
        CommandType.STOP_FOCUS -> RemoteCommand.StopFocus
        CommandType.APPROVE_REQUEST -> parseApprove(p)
        CommandType.DENY_REQUEST -> p.str("reqId")?.let { RemoteCommand.DenyRequest(it) }
        CommandType.SYNC_BLOCKS -> RemoteCommand.SyncBlocks
        CommandType.REMIND_PERMISSION -> p.str("permission")?.let { RemoteCommand.RemindPermission(it) }
    }
}

private class PayloadReader(
    private val payload: Map<String, Any?>,
) {
    fun str(key: String) = (payload[key] as? String)?.takeIf { it.isNotBlank() }

    fun int(key: String) = (payload[key] as? Number)?.toInt()
}

private fun parseApprove(p: PayloadReader): RemoteCommand? {
    val reqId = p.str("reqId")
    val mins = p.int("mins")
    return if (reqId != null && mins != null) RemoteCommand.ApproveRequest(reqId, mins) else null
}
