package com.dculus.stayfocused.core.sync

import java.security.SecureRandom
import java.util.Base64

/** Pure helpers for the one-time link token the Kids phone shows as a QR code. */
object LinkTokens {
    /** How long a token lives; the rules reject a longer one. */
    const val TTL_MILLIS = 5 * 60 * 1000L

    /** Subtracted from the TTL so a Kids clock that runs a little fast still passes the "<= 5 min" rule. */
    const val CLOCK_SKEW_MARGIN_MILLIS = 5_000L

    private const val TOKEN_BYTES = 16
    private const val CODE_DIGITS = 6
    private const val CODE_BOUND = 1_000_000
    private const val SCHEME_PREFIX = "stayfocused://link?t="
    private const val MILLIS_PER_SECOND = 1000L
    private const val SECONDS_PER_MINUTE = 60L

    private val tokenPattern = Regex("^[A-Za-z0-9_-]{22}$")

    /** The stored expiry for a token made at [nowMillis]: whole seconds, with the clock-skew margin taken off. */
    fun expiryFor(nowMillis: Long): Long =
        (nowMillis + TTL_MILLIS - CLOCK_SKEW_MARGIN_MILLIS) / MILLIS_PER_SECOND * MILLIS_PER_SECOND

    /** 128 random bits as base64url without padding: 22 characters. */
    fun newToken(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** The 6-digit code both phones show at the Consent step. */
    fun newCode(random: SecureRandom = SecureRandom()): String =
        random.nextInt(CODE_BOUND).toString().padStart(CODE_DIGITS, '0')

    /** What the QR code encodes. */
    fun uri(token: String): String = "$SCHEME_PREFIX$token"

    /** The token inside a scanned `stayfocused://link?t=...` text, or null for anything else. */
    fun tokenFrom(scanned: String): String? =
        scanned
            .trim()
            .takeIf { it.startsWith(SCHEME_PREFIX) }
            ?.removePrefix(SCHEME_PREFIX)
            ?.takeIf { tokenPattern.matches(it) }

    /** `m:ss` for the countdown, never negative, rounded up: 291_200 -> "4:52". */
    fun formatRemaining(millis: Long): String {
        val seconds = (millis.coerceAtLeast(0) + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
        return "${seconds / SECONDS_PER_MINUTE}:${(seconds % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"
    }
}
