package com.dculus.stayfocused.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkCodeTest {
    @Test
    fun sixDigitsAreShownInTwoGroups() {
        assertEquals("482 913", formatLinkCode("482913"))
        assertEquals("000 042", formatLinkCode("000042"))
    }

    @Test
    fun anythingElseIsLeftAsItIs() {
        assertEquals("", formatLinkCode(""))
        assertEquals("12345", formatLinkCode("12345"))
        assertEquals("1234567", formatLinkCode("1234567"))
    }
}
