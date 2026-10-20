@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.v5.components

/** One dot centre, in the same unit as the dot pitch passed to [dotMatrixLayout] (the unit is up to the caller). */
data class DotCentre(
    val x: Float,
    val y: Float,
)

/** Result of [dotMatrixLayout]: dot [centres], and the overall [width] and [height] of the numerals. */
data class DotMatrixLayout(
    val centres: List<DotCentre>,
    val width: Float,
    val height: Float,
)

/** Rows of every glyph are 7 dots tall. */
const val DOT_MATRIX_ROWS = 7

/** Radius of one dot as a fraction of the dot pitch (`r = .34 s`). */
const val DOT_MATRIX_RADIUS_FRACTION = 0.34f

/** A space advances this many cells. */
private const val SPACE_CELLS = 2

/** The glyph map `F` of the v5 prototype (line 512): `1` is a dot. Digits are 5 columns, `:` is 1. */
private val GLYPHS: Map<Char, List<String>> =
    mapOf(
        '0' to listOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
        '1' to listOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
        '2' to listOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
        '3' to listOf("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
        '4' to listOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
        '5' to listOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
        '6' to listOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
        '7' to listOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
        '8' to listOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
        '9' to listOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
        ':' to listOf("0", "0", "1", "0", "1", "0", "0"),
    )

/** Whether [DotMatrixText] draws [char] (0–9 and `:`). Spaces advance, anything else is skipped. */
fun isDotMatrixGlyph(char: Char): Boolean = char in GLYPHS

/**
 * Lays [text] out as 5 × 7 dot glyphs with dot pitch [s] (prototype `dm()`, line 531).
 *
 * Each glyph is `columns + 1` cells wide (one blank column after it); a space advances 2 cells; any other
 * character is skipped. Dots sit at the centre of their cell, so a dot at column `c`, row `r` of a glyph starting at
 * `x` has its centre at `(x + c·s + s/2, r·s + s/2)`. Width is the advance minus the trailing blank column, but at
 * least one cell; height is 7 cells. Every digit advances the same, so a running countdown doesn't jitter.
 */
fun dotMatrixLayout(
    text: String,
    s: Float,
): DotMatrixLayout {
    val centres = mutableListOf<DotCentre>()
    var x = 0f
    for (char in text) {
        val glyph = GLYPHS[char]
        if (glyph != null) {
            glyph.forEachIndexed { row, bits ->
                bits.forEachIndexed { column, bit ->
                    if (bit == '1') centres += DotCentre(x + column * s + s / 2f, row * s + s / 2f)
                }
            }
            x += (glyph[0].length + 1) * s
        } else if (char == ' ') {
            x += SPACE_CELLS * s
        }
    }
    return DotMatrixLayout(centres, width = maxOf(s, x - s), height = DOT_MATRIX_ROWS * s)
}
