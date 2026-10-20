package com.dculus.stayfocused.core.ui.v5.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakTicksTest {
    private val ticks = breakTicks()

    @Test
    fun hasTwentyThreeTicksWithTheLimeOneInTheMiddle() {
        assertEquals(23, ticks.size)
        assertEquals(listOf(11), ticks.indices.filter { ticks[it].isCentre })
        assertEquals(3f, ticks[11].width)
        assertEquals(34f, ticks[11].height)
        assertEquals(1f, ticks[11].alpha)
    }

    @Test
    fun otherTicksAreTwoWideAndTwentyOrThirteenTall() {
        ticks.filterNot { it.isCentre }.forEach { assertEquals(2f, it.width) }
        listOf(1, 6, 16, 21).forEach { assertEquals(20f, ticks[it].height) }
        listOf(0, 2, 3, 4, 5, 22).forEach { assertEquals(13f, ticks[it].height) }
    }

    @Test
    fun alphaFadesFromTheCentreSymmetricallyWithAFloor() {
        assertEquals(0.83f, ticks[10].alpha, 1e-6f)
        assertEquals(0.83f, ticks[12].alpha, 1e-6f)
        assertEquals(0.2f, ticks[0].alpha, 1e-6f)
        assertEquals(0.2f, ticks[22].alpha, 1e-6f)
        for (i in 0..10) assertEquals(ticks[i].alpha, ticks[22 - i].alpha, 1e-6f)
        for (i in 0 until 11) assertTrue(ticks[i].alpha <= ticks[i + 1].alpha)
    }
}

class LinkDotsTest {
    @Test
    fun leftPhoneShowsAllNineDotsFullyBright() {
        for (r in 0..2) for (c in 0..2) assertFalse(linkDotDimmed(rightPhone = false, row = r, column = c))
    }

    @Test
    fun rightPhoneDimsTheFourOddCells() {
        val dimmed =
            (0..2)
                .flatMap { r -> (0..2).map { c -> r to c } }
                .filter { (r, c) -> linkDotDimmed(rightPhone = true, row = r, column = c) }
        assertEquals(listOf(0 to 1, 1 to 0, 1 to 2, 2 to 1), dimmed)
    }
}
