package com.dculus.stayfocused.push

/** What the `notifyParent` and `unlinkDevice` functions push (data-only FCM messages built on the server). */
internal sealed interface PushMessage {
    val deviceId: String

    /** A child asks for [minutes] more of [app] (`type = request`). */
    data class Request(
        override val deviceId: String,
        val reqId: String,
        val device: String,
        val app: String,
        val minutes: Int,
    ) : PushMessage

    /** A child unlinked its phone (`type = unlinked`). */
    data class Unlinked(
        override val deviceId: String,
        val device: String,
    ) : PushMessage

    companion object {
        private val idPattern = Regex("^[A-Za-z0-9_-]{1,128}$")
        private const val MAX_TEXT = 100
        private const val MAX_MINUTES = 480

        /** Reads a message; null for anything that does not fit, so a malformed push shows nothing. */
        fun parse(data: Map<String, String>): PushMessage? {
            val deviceId = data["deviceId"]?.takeIf(idPattern::matches)
            val device = data["device"]?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_TEXT)
            return when {
                deviceId == null || device == null -> null
                data["type"] == "request" -> parseRequest(data, deviceId, device)
                data["type"] == "unlinked" -> Unlinked(deviceId, device)
                else -> null
            }
        }

        private fun parseRequest(
            data: Map<String, String>,
            deviceId: String,
            device: String,
        ): PushMessage? {
            val reqId = data["reqId"]?.takeIf(idPattern::matches)
            val app = data["app"]?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_TEXT)
            val minutes = data["minutes"]?.toIntOrNull()?.takeIf { it in 1..MAX_MINUTES }
            return if (reqId != null && app != null && minutes != null) {
                Request(deviceId, reqId, device, app, minutes)
            } else {
                null
            }
        }
    }
}

/** The answers a notification offers; 0 minutes is No. */
internal val NOTIFICATION_APPROVALS: List<Int> = listOf(15, 30, 60)

/** The answer a notification button carries in its intent. */
internal data class RequestAnswer(
    val deviceId: String,
    val reqId: String,
    /** Minutes to allow, 0 to deny. */
    val minutes: Int,
)
