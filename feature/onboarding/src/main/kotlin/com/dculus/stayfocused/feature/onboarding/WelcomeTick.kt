package com.dculus.stayfocused.feature.onboarding

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** One tick of the Welcome ring, in the prototype's 240 x 240 viewBox. [lit] ticks are drawn lime. */
internal data class WelcomeTick(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val lit: Boolean,
    val major: Boolean,
)

private const val TICK_COUNT = 60
private const val DEGREES_PER_TICK = 6
private const val CENTRE = 120f
private const val OUTER_RADIUS = 92f
private const val MAJOR_INNER_RADIUS = 80f
private const val MINOR_INNER_RADIUS = 86f
private const val MAJOR_EVERY = 5
private const val LIT_FROM_DEGREES = 240
private const val LIT_TO_DEGREES = 366
private const val LIT_WRAP_DEGREES = 6

private fun pointAt(
    radius: Float,
    degrees: Int,
): Pair<Float, Float> {
    val t = degrees * PI / 180
    return (CENTRE + radius * sin(t)).toFloat() to (CENTRE - radius * cos(t)).toFloat()
}

/** Prototype `WELCOME_TICKS`: 60 ticks, every 6 degrees clockwise from 12 o'clock, majors every 5th. */
internal val welcomeTicks: List<WelcomeTick> =
    List(TICK_COUNT) { i ->
        val angle = i * DEGREES_PER_TICK
        val major = i % MAJOR_EVERY == 0
        val (x1, y1) = pointAt(if (major) MAJOR_INNER_RADIUS else MINOR_INNER_RADIUS, angle)
        val (x2, y2) = pointAt(OUTER_RADIUS, angle)
        WelcomeTick(
            x1 = x1,
            y1 = y1,
            x2 = x2,
            y2 = y2,
            lit = angle in LIT_FROM_DEGREES..LIT_TO_DEGREES || angle <= LIT_WRAP_DEGREES,
            major = major,
        )
    }
