package com.dculus.stayfocused.core.model

import kotlin.math.roundToInt

private const val FULL_TURN = 360.0

/**
 * Dial value for a pointer angle, in degrees clockwise from 12 o'clock (prototype `dialDown`).
 * `round(angle / 360 x max / step) x step`, clamped to `[step, max]`; a raw 0 (the top of the dial) maps to `max`.
 */
fun dialValueForAngle(
    angleDeg: Double,
    config: DialConfig,
): Int {
    val angle = ((angleDeg % FULL_TURN) + FULL_TURN) % FULL_TURN
    val raw = (angle / FULL_TURN * config.max / config.step).roundToInt() * config.step
    return (if (raw == 0) config.max else raw).coerceIn(config.step, config.max)
}

/** Big number in the dial centre: "45", "1", "1:30". */
fun dialCenterLabel(value: Int): String =
    when {
        value < 60 -> "$value"
        value % 60 != 0 -> "${value / 60}:${(value % 60).toString().padStart(2, '0')}"
        else -> "${value / 60}"
    }

enum class DialUnit { MINUTES, HOUR, HOURS }

/** Unit under the centre number: MINUTES below an hour, HOUR for exactly 60, otherwise HOURS. */
fun dialUnit(value: Int): DialUnit =
    when {
        value < 60 -> DialUnit.MINUTES
        value == 60 -> DialUnit.HOUR
        else -> DialUnit.HOURS
    }
