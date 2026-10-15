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
        handler.onCode(valid)
        handler.onCode(valid)
        assertEquals(listOf("abcdefghijklmnopqrstuv"), tokens)
        assertEquals(0, invalid)
    }

    @Test
    fun invalidCodeToastsOnceThenAgainAfterTheCooldown() {
        handler.onCode("hello")
        handler.onCode("hello")
        assertEquals(1, invalid)
        time += INVALID_CODE_COOLDOWN_MS
        handler.onCode("hello")
        assertEquals(2, invalid)
    }

    @Test
    fun aDifferentInvalidCodeToastsImmediately() {
        handler.onCode("one")
        handler.onCode("two")
        assertEquals(2, invalid)
    }

    @Test
    fun aValidCodeStillWorksAfterAnInvalidOne() {
        handler.onCode("nope")
        handler.onCode(valid)
        assertEquals(1, tokens.size)
    }
}
