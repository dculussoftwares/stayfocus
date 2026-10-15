package com.dculus.stayfocused.feature.devices

import java.net.URI

/** Reads the pairing token out of the QR code the Kids app shows (`stayfocused://link?t={token}`). */
internal object LinkCodeParser {
    private const val SCHEME = "stayfocused"
    private const val HOST = "link"
    private const val PARAM = "t"

    // 128 random bits as unpadded base64url is 22 characters; allow some slack for format changes.
    private val TOKEN = Regex("[A-Za-z0-9_-]{16,128}")

    /** The token, or null when [raw] is not a Stay Focused link code. */
    fun parseToken(raw: String): String? {
        val uri = runCatching { URI(raw.trim()) }.getOrNull()
        val query = uri?.takeIf { it.scheme == SCHEME && it.host == HOST }?.rawQuery
        val token =
            query
                ?.split('&')
                ?.map { it.substringBefore('=') to it.substringAfter('=', "") }
                ?.firstOrNull { it.first == PARAM }
                ?.second
        return token?.takeIf { TOKEN.matches(it) }
    }
}
