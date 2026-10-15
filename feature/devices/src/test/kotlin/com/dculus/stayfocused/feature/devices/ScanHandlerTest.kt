package com.dculus.stayfocused.feature.devices

import org.junit.Test
import kotlin.test.assertEquals

class ScanHandlerTest {
    private val valid = "stayfocused://link?t=abcdefghijklmnopqrstuv"
    private var time = 0L
    private val tokens = mutableListOf<String>()
    private var invalid = 0
    private val handler = ScanHandler({ tokens += it }, { invalid++ }, { time })

    @Test
    fun deliversTheTokenOnceEvenIfTheCodeStaysInView() {
        handler.onCodes(listOf(valid))
        handler.onCodes(listOf(valid))
        assertEquals(listOf("abcdefghijklmnopqrstuv"), tokens)
        assertEquals(0, invalid)
    }

    @Test
    fun invalidCodeToastsOnceThenAgainAfterTheCooldown() {
        handler.onCodes(listOf("hello"))
        handler.onCodes(listOf("hello"))
        assertEquals(1, invalid)
        time += INVALID_CODE_COOLDOWN_MS
        handler.onCodes(listOf("hello"))
        assertEquals(2, invalid)
    }

    @Test
    fun aDifferentInvalidCodeToastsImmediately() {
        handler.onCodes(listOf("one"))
        handler.onCodes(listOf("two"))
        assertEquals(2, invalid)
    }

    @Test
    fun aValidCodeStillWorksAfterAnInvalidOne() {
        handler.onCodes(listOf("nope"))
        handler.onCodes(listOf(valid))
        assertEquals(1, tokens.size)
    }

    @Test
    fun aValidCodeBesideAnotherCodeIsStillFound() {
        handler.onCodes(listOf("https://example.com", valid))
        assertEquals(1, tokens.size)
        assertEquals(0, invalid)
    }
}
