@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.art

import kotlin.math.abs
import kotlin.math.max

/** One tick of the break art's ruler: size in dp, white [alpha] (the centre tick is lime and fully opaque). */
internal data class BreakTick(
    val width: Float,
    val height: Float,
    val alpha: Float,
    val isCentre: Boolean,
)

private const val BREAK_TICK_COUNT = 23
private const val BREAK_CENTRE = BREAK_TICK_COUNT / 2

/**
 * The 23 ticks (V5 prototype `breakArt`): the centre one is 3 × 34 (lime); the others are 2 wide, 20 tall where
 * `index % 5 == 1` and 13 otherwise, with alpha fading from .9 by .07 per step away from the centre, floor .2.
 */
internal fun breakTicks(): List<BreakTick> =
    List(BREAK_TICK_COUNT) { i ->
        if (i == BREAK_CENTRE) {
            BreakTick(width = 3f, height = 34f, alpha = 1f, isCentre = true)
        } else {
            BreakTick(
                width = 2f,
                height = if (i % 5 == 1) 20f else 13f,
                alpha = max(0.2f, 0.9f - abs(i - BREAK_CENTRE) * 0.07f),
                isCentre = false,
            )
        }
    }
