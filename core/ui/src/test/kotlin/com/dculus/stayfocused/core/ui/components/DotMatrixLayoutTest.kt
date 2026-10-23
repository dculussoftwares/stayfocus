package com.dculus.stayfocused.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DotMatrixLayoutTest {
    private val dotsPerDigit =
        mapOf(
            '0' to 19,
            '1' to 10,
            '2' to 14,
            '3' to 15,
            '4' to 14,
            '5' to 17,
            '6' to 15,
            '7' to 11,
            '8' to 17,
            '9' to 15,
        )

    @Test
    fun `every digit has the prototype's dot count and stays inside its 5 by 7 cell box`() {
        dotsPerDigit.forEach { (digit, count) ->
            val layout = dotMatrixLayout(digit.toString(), 1f)
            assertEquals("dots in $digit", count, layout.centres.size)
            assertEquals("width of $digit", 5f, layout.width, 0f)
            assertEquals("height of $digit", 7f, layout.height, 0f)
            assertTrue(layout.centres.all { it.x in 0.5f..4.5f && it.y in 0.5f..6.5f })
        }
    }

    @Test
    fun `digit one is a stem with a base and a flag`() {
        val xs = dotMatrixLayout("1", 1f).centres.map { it.x to it.y }
        assertEquals((2.5f to 0.5f), xs.first())
        assertTrue((1.5f to 1.5f) in xs)
        assertEquals(listOf(1.5f, 2.5f, 3.5f), xs.filter { it.second == 6.5f }.map { it.first })
    }

    @Test
    fun `colon is two dots in one column`() {
        val layout = dotMatrixLayout(":", 1f)
        assertEquals(listOf(DotCentre(0.5f, 2.5f), DotCentre(0.5f, 4.5f)), layout.centres)
        assertEquals(1f, layout.width, 0f)
    }

    @Test
    fun `space advances two cells and draws nothing`() {
        val layout = dotMatrixLayout("1 1", 1f)
        assertEquals(20, layout.centres.size)
        // 1: advance 6, space 2, 1: advance 6, minus the trailing blank column.
        assertEquals(13f, layout.width, 0f)
        val secondOne = layout.centres.filter { it.x >= 8f }
        assertEquals(10, secondOne.size)
        assertEquals(10.5f, secondOne.first().x, 0f)
        assertTrue(layout.centres.none { it.x > 5f && it.x < 8f })
    }

    @Test
    fun `unknown characters are skipped without advancing`() {
        assertEquals(dotMatrixLayout("12", 1f), dotMatrixLayout("a1-2é", 1f))
    }

    @Test
    fun `empty or unknown text is one cell wide`() {
        assertEquals(DotMatrixLayout(emptyList(), 4f, 28f), dotMatrixLayout("", 4f))
        assertEquals(dotMatrixLayout("", 4f), dotMatrixLayout("x", 4f))
    }

    @Test
    fun `layout scales with the dot pitch`() {
        val one = dotMatrixLayout("2:33", 1f)
        val five = dotMatrixLayout("2:33", 5f)
        assertEquals(one.width * 5f, five.width, 0.001f)
        assertEquals(one.height * 5f, five.height, 0.001f)
        one.centres.zip(five.centres).forEach { (a, b) ->
            assertEquals(a.x * 5f, b.x, 0.001f)
            assertEquals(a.y * 5f, b.y, 0.001f)
        }
    }

    @Test
    fun `width does not depend on which digits are shown`() {
        val widths = ('0'..'9').map { dotMatrixLayout("$it$it:$it$it", 4f).width }.toSet()
        assertEquals(1, widths.size)
        assertEquals(25f * 4f, widths.single(), 0f)
    }

    @Test
    fun `layout of 2 colon 33 matches the prototype advance`() {
        // 2(6) + :(2) + 3(6) + 3(6) = 20 cells, minus the trailing blank column.
        assertEquals(19f * 8f, dotMatrixLayout("2:33", 8f).width, 0f)
        assertEquals(37f * 5f, dotMatrixLayout("482 913", 5f).width, 0f)
        assertTrue(isDotMatrixGlyph('7') && isDotMatrixGlyph(':') && !isDotMatrixGlyph(' ') && !isDotMatrixGlyph('a'))
    }
}
