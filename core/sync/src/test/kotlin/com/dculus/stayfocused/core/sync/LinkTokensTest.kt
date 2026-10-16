package com.dculus.stayfocused.core.sync

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkTokensTest {
    @Test
    fun tokenIs128BitsOfBase64urlWithoutPadding() {
        val token = LinkTokens.newToken()
        assertEquals(22, token.length)
        assertTrue(token.all { it.isLetterOrDigit() || it == '-' || it == '_' }, token)
    }

    @Test
    fun tokensDiffer() {
        assertNotEquals(LinkTokens.newToken(), LinkTokens.newToken())
    }

    @Test
    fun codeIsSixDigits() {
        repeat(200) { assertTrue(Regex("^[0-9]{6}$").matches(LinkTokens.newCode())) }
    }

    @Test
    fun uriRoundTripsThroughTheScannerParser() {
        val token = LinkTokens.newToken()
        assertEquals("stayfocused://link?t=$token", LinkTokens.uri(token))
        assertEquals(token, LinkTokens.tokenFrom(LinkTokens.uri(token)))
    }

    @Test
    fun parserRejectsOtherTexts() {
        val token = LinkTokens.newToken()
        assertNull(LinkTokens.tokenFrom("https://example.com/?t=$token"))
        assertNull(LinkTokens.tokenFrom("stayfocused://link?t=short"))
        assertNull(LinkTokens.tokenFrom("stayfocused://link?t=$token&x=1"))
        assertNull(LinkTokens.tokenFrom(""))
    }

    @Test
    fun countdownFormatsMinutesAndSeconds() {
        assertEquals("5:00", LinkTokens.formatRemaining(LinkTokens.TTL_MILLIS))
        assertEquals("4:52", LinkTokens.formatRemaining(291_200))
        assertEquals("0:01", LinkTokens.formatRemaining(1))
        assertEquals("0:00", LinkTokens.formatRemaining(-5))
    }
}
